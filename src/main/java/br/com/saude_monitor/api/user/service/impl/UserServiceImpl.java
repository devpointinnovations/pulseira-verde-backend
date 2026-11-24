package br.com.saude_monitor.api.user.service.impl;

import br.com.saude_monitor.api.config.security.UsuarioAutenticadoCache;
import br.com.saude_monitor.api.feedback.service.FeedbackConsulta;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import br.com.saude_monitor.api.auth.service.AuthService;
import br.com.saude_monitor.api.visita.service.VisitaConsulta;
import br.com.saude_monitor.api.config.exception.ConflitoException;
import br.com.saude_monitor.api.config.exception.RecursoNaoEncontradoException;
import br.com.saude_monitor.api.config.exception.ValidacaoNegocioException;
import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import br.com.saude_monitor.api.user.document.ConsentimentosDocument;
import br.com.saude_monitor.api.user.document.Papel;
import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.user.dto.AtualizarConsentimentosRequest;
import br.com.saude_monitor.api.user.dto.ConsentimentoRequest;
import br.com.saude_monitor.api.user.dto.ConsentimentosResponse;
import br.com.saude_monitor.api.user.dto.UserRequest;
import br.com.saude_monitor.api.user.dto.UserResponse;
import br.com.saude_monitor.api.user.repository.UserRepository;
import br.com.saude_monitor.api.user.service.UserService;
import br.com.saude_monitor.api.config.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementação do cadastro de usuários (F0-01) e da exclusão de conta LGPD (F0-05).
 *
 * <p>A senha é armazenada <strong>apenas</strong> como hash BCrypt ({@code senhaHash}),
 * nunca em texto puro. O papel padrão é {@code USER} e os consentimentos LGPD são
 * registrados com aceite dos termos de uso (obrigatório).</p>
 *
 * <p>Na exclusão de conta (F0-05), o dado pessoal identificável é removido
 * (documento de usuário — a senha vive apenas como hash nesse mesmo documento, não há
 * coleção de autenticação separada) e os dados usados nas estatísticas
 * (visitas e feedbacks) são anonimizados — {@code usuarioId} zerado, e saem também o
 * {@code dispositivoId} e os pontos de GPS das visitas e o comentário dos feedbacks —
 * preservando os
 * indicadores públicos (nota/tempo), que nunca referenciam identidade.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String VERSAO_TERMOS = "1.0";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VisitaConsulta visitaConsulta;
    private final FeedbackConsulta feedbackConsulta;
    private final IndicadorService indicadorService;
    private final AuthService authService;
    private final UsuarioAutenticadoCache usuarioAutenticadoCache;

    @Override
    public UserResponse saveUser(UserRequest request) {
        var now = Instant.now();
        var normalizedEmail = EmailNormalizer.normalizar(request.email());

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ConflitoException("Email já cadastrado.");
        }

        ConsentimentoRequest consent = request.consentimento();
        if (consent == null || !consent.termosUso()) {
            throw new ValidacaoNegocioException("Aceite dos termos de uso é obrigatório para criar a conta.");
        }

        var user = userRepository.save(UserDocument.builder()
                .fullName(request.fullName())
                .email(normalizedEmail)
                .phone(request.phone())
                .senhaHash(passwordEncoder.encode(request.password()))
                .papel(Papel.USER)
                .consentimentos(consentimentosRegistrados(consent, now))
                .active(true)
                .emailVerificado(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Confirmação obrigatória de e-mail (10/09/2026): sem isto, um e-mail com erro de
        // digitação ou inexistente nunca recebe o código de "esqueci minha senha" — a
        // conta fica sem recuperação possível. O login fica bloqueado até confirmar
        // (AuthServiceImpl.login()).
        authService.enviarCodigoConfirmacaoEmail(normalizedEmail);

        return new UserResponse(
                true,
                "Usuário cadastrado com sucesso",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @Override
    public ConsentimentosResponse atualizarConsentimentos(String usuarioId, AtualizarConsentimentosRequest request) {
        if (request == null || request.vazio()) {
            throw new ValidacaoNegocioException(
                    "Informe ao menos uma finalidade (localizacao ou notificacoes) para atualizar.");
        }

        UserDocument user = userRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        ConsentimentosDocument consentimentos = user.getConsentimentos() == null
                ? ConsentimentosDocument.builder().build()
                : user.getConsentimentos();

        Instant now = Instant.now();
        if (request.localizacao() != null) {
            consentimentos.setLocalizacao(
                    decidir(consentimentos.getLocalizacao(), request.localizacao(), request.versaoTermos(), now));
        }
        if (request.notificacoes() != null) {
            consentimentos.setNotificacoes(
                    decidir(consentimentos.getNotificacoes(), request.notificacoes(), request.versaoTermos(), now));
        }

        user.setConsentimentos(consentimentos);
        user.setUpdatedAt(now);
        userRepository.save(user);

        // Trilha de auditoria da decisão (art. 8º §5º): registra o que mudou, sem
        // qualquer dado pessoal além do identificador interno.
        log.info("Consentimentos do usuário {} atualizados (LGPD): localizacao={}, notificacoes={}.",
                usuarioId, request.localizacao(), request.notificacoes());

        return ConsentimentosResponse.de(consentimentos);
    }

    /**
     * Monta o registro da decisão. A versão do aviso é mantida quando o app não a
     * informa — não podemos "apagar" a versão sob a qual o aceite original foi dado.
     */
    private ConsentimentoItem decidir(ConsentimentoItem atual, boolean aceito, String versao, Instant now) {
        String versaoRegistrada = versao == null || versao.isBlank()
                ? (atual == null ? null : atual.getVersao())
                : versao;
        return ConsentimentoItem.builder()
                .aceito(aceito)
                .data(now)
                .versao(versaoRegistrada)
                .build();
    }

    @Override
    public Map<String, Object> exportarDados(String usuarioId) {
        UserDocument user = userRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", Instant.now());
        dados.put("usuario", Map.of(
                "nome", user.getFullName(),
                "email", user.getEmail(),
                "telefone", user.getPhone() == null ? "" : user.getPhone(),
                "criadoEm", user.getCreatedAt(),
                "consentimentos", user.getConsentimentos()
        ));
        dados.put("visitas", visitaConsulta.doUsuario(user.getId()));
        dados.put("feedbacks", feedbackConsulta.doUsuario(user.getId()));
        return dados;
    }

    @Override
    public void excluirConta(String usuarioId) {
        UserDocument user = userRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        // Coleta os hospitais afetados ANTES de anonimizar, para recalcular os indicadores
        // e refletir a exclusão imediatamente (os dados permanecem, então os valores não
        // mudam, mas o job de 15min não precisa esperar).
        Set<String> hospitalIds = new LinkedHashSet<>(visitaConsulta.hospitaisDoUsuario(usuarioId));
        hospitalIds.addAll(feedbackConsulta.hospitaisDoUsuario(usuarioId));

        // Anonimiza visitas e feedbacks (dados de estatística): desvincula o usuário.
        visitaConsulta.anonimizarDoUsuario(usuarioId);
        feedbackConsulta.anonimizarDoUsuario(usuarioId);

        // Remove o documento de usuário (dado pessoal identificável). A senha vive apenas
        // como hash neste mesmo documento — não há coleção de autenticação separada.
        userRepository.delete(user);
        // O token ainda válido desta conta deixa de autenticar já nesta instância (ARQ-03).
        usuarioAutenticadoCache.removerDoCache(user.getEmail());

        log.info("Conta do usuário {} excluída (LGPD). Dados pessoais removidos; visitas/feedbacks anonimizados.",
                usuarioId);

        // Recalcula indicadores dos hospitais afetados (best-effort).
        for (String hospitalId : hospitalIds) {
            try {
                indicadorService.recalcular(hospitalId);
            } catch (Exception ex) {
                log.warn("Falha ao recalcular indicador do hospital {} após exclusão de conta: {}",
                        hospitalId, ex.getMessage());
            }
        }
    }

    /**
     * Registra os consentimentos LGPD do titular no cadastro. O aceite dos termos de uso
     * é obrigatório e vem explícito no payload (§3.1 — {@code consentimento.termosUso});
     * localização e notificações ficam pendentes até o usuário optar explicitamente.
     */
    private ConsentimentosDocument consentimentosRegistrados(ConsentimentoRequest consent, Instant now) {
        String versao = consent.versaoTermos() == null || consent.versaoTermos().isBlank()
                ? VERSAO_TERMOS
                : consent.versaoTermos();
        return ConsentimentosDocument.builder()
                .termosUso(ConsentimentoItem.builder().aceito(true).data(now).versao(versao).build())
                .localizacao(ConsentimentoItem.builder().aceito(false).data(null).versao(null).build())
                .notificacoes(ConsentimentoItem.builder().aceito(false).data(null).versao(null).build())
                .build();
    }
}
