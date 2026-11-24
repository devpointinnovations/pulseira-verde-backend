package br.com.saude_monitor.api.feedback.seed;

import br.com.saude_monitor.api.config.trava.TravaDistribuida;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.hospital.dto.HospitalParaMassa;
import br.com.saude_monitor.api.hospital.service.HospitalConsulta;
import br.com.saude_monitor.api.user.dto.UsuarioDeMassa;
import br.com.saude_monitor.api.user.service.UsuarioMassa;
import br.com.saude_monitor.api.visita.service.VisitaMassa;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Injeta a massa de dados de avaliações no ambiente de desenvolvimento: usuários de
 * teste, visitas finalizadas e avaliações distribuídas em parte dos hospitais ativos,
 * seguidas do recálculo dos indicadores — o suficiente para testar a avaliação, a
 * visualização dos indicadores e o ranking (E4-05) sem precisar de visitas reais.
 *
 * <p><b>Nunca roda fora de desenvolvimento.</b> São três travas independentes: o perfil
 * Spring {@code dev}, a propriedade {@code app.massa-avaliacoes.enabled=true} (padrão
 * {@code false}) e o nome do banco, que precisa conter {@code dev} ou {@code test} e não
 * pode conter {@code hom} nem {@code prod}.</p>
 *
 * <p><b>Idempotente:</b> todo documento gerado tem {@code _id} com o prefixo
 * {@value MassaAvaliacoesGerador#PREFIXO_ID}, e só eles são apagados ou regerados. Com a
 * massa completa, o boot só recalcula os indicadores e sincroniza a senha. A massa é regerada
 * com datas atuais quando tem mais de 30 dias (antes de sair da janela de 90 dias, RN-14),
 * quando sobrou uma carga interrompida ou no modo {@code recriar}.</p>
 *
 * <p><b>Fora do caminho de startup:</b> roda em segundo plano depois de
 * {@link ApplicationReadyEvent} (os seeds de hospitais já terminaram). Como
 * {@code ApplicationRunner}, a carga segurava o readiness em {@code OUT_OF_SERVICE}: no
 * Cloud Run a CPU fica quase parada fora de requisição depois que a porta abre, a carga
 * não terminava a tempo e o smoke test do deploy do dev (29/09/2026) recebia 503 em
 * {@code /actuator/health}.</p>
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.massa-avaliacoes", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MassaAvaliacoesRunner {

    /** Semente fixa: a mesma base de hospitais gera sempre a mesma distribuição. */
    static final long SEMENTE = 20260929L;

    static final int QUANTIDADE_USUARIOS = 30;

    /** Nome da trava da carga ({@link TravaDistribuida}). */
    static final String NOME_TRAVA = "massa-avaliacoes";

    private static final Duration VALIDADE_TRAVA = Duration.ofMinutes(15);

    static final String DOMINIO_EMAIL = "radarsaude.invalid";

    /** Idade a partir da qual a massa é regerada sozinha: antes de sair da janela de 90 dias (RN-14). */
    static final Duration VALIDADE_MASSA = Duration.ofDays(30);

    /** O banco precisa indicar desenvolvimento ou teste... */
    private static final Pattern BANCO_PERMITIDO = Pattern.compile(".*(dev|test).*");

    /** ...e não pode indicar homologação ou produção. */
    private static final Pattern BANCO_PROIBIDO = Pattern.compile(".*(hom|prod).*");

    private static final Pattern PREFIXO = Pattern.compile("^" + Pattern.quote(MassaAvaliacoesGerador.PREFIXO_ID));

    /** Usuário gravado primeiro em toda carga; o {@code createdAt} dele é a data da carga. */
    static final String PRIMEIRO_USUARIO = MassaAvaliacoesGerador.PREFIXO_ID + "usuario-01";

    static final List<String> NOMES = List.of(
            "Ana", "Bruno", "Carla", "Diego", "Elaine", "Fábio", "Gabriela", "Heitor", "Isabela", "João",
            "Karina", "Lucas", "Marina", "Nelson", "Olívia", "Paulo", "Quitéria", "Rafael", "Sílvia", "Tiago",
            "Úrsula", "Vinícius", "Wanda", "Xavier", "Yara", "Zeca", "Beatriz", "Caio", "Débora", "Eduardo");

    private final MongoTemplate mongoTemplate;
    private final HospitalConsulta hospitalConsulta;
    private final UsuarioMassa usuarioMassa;
    private final VisitaMassa visitaMassa;
    private final PasswordEncoder passwordEncoder;
    private final IndicadorService indicadorService;
    private final MassaAvaliacoesProperties properties;

    /** Duas instâncias do dev subindo juntas não regeram a massa ao mesmo tempo (SOL-02). */
    private final TravaDistribuida trava;

    /** Dispara a carga em segundo plano quando a aplicação está pronta. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void aoFicarPronta() {
        executarCarga();
    }

    /**
     * A massa é opcional: qualquer falha é registrada e a aplicação segue — um erro aqui
     * não pode derrubar o ambiente de desenvolvimento.
     */
    public void executarCarga() {
        try {
            trava.executar(NOME_TRAVA, VALIDADE_TRAVA, this::executar);
        } catch (RuntimeException e) {
            log.error("[MassaAvaliacoes] Falha na carga da massa de avaliações; a aplicação segue sem ela.", e);
        }
    }

    private void executar() {
        String banco = mongoTemplate.getDb().getName();
        String nome = banco.toLowerCase(Locale.ROOT);
        if (!BANCO_PERMITIDO.matcher(nome).matches() || BANCO_PROIBIDO.matcher(nome).matches()) {
            log.error("[MassaAvaliacoes] Banco '{}' não é de desenvolvimento (o nome precisa conter "
                    + "'dev' ou 'test' e não conter 'hom' nem 'prod'). Carga RECUSADA.", banco);
            return;
        }

        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Query daMassa = daMassa();
        boolean completa = mongoTemplate.exists(daMassa, FeedbackDocument.class);
        boolean resto = completa
                || usuarioMassa.existeComPrefixo(MassaAvaliacoesGerador.PREFIXO_ID)
                || visitaMassa.existeComPrefixo(MassaAvaliacoesGerador.PREFIXO_ID);

        if (completa && !properties.recriar() && !vencida(agora)) {
            sincronizarSenha(agora);
            // Recalcula a cada boot: sem atividade recente, o job de 15 min não olharia
            // para estes hospitais e os indicadores ficariam congelados no dia da carga.
            List<String> hospitais = mongoTemplate.findDistinct(daMassa, "hospitalId", FeedbackDocument.class, String.class);
            hospitais.forEach(indicadorService::recalcular);
            log.info("[MassaAvaliacoes] Massa já presente no banco '{}'; indicadores de {} hospitais recalculados.",
                    banco, hospitais.size());
            return;
        }

        // Massa pedida de novo, vencida, ou resto de uma carga interrompida: apaga só ela.
        Set<String> hospitaisAfetados = resto ? apagarMassa() : new HashSet<>();

        List<HospitalParaMassa> hospitais = hospitalConsulta.ativosParaMassa();
        if (hospitais.isEmpty()) {
            log.warn("[MassaAvaliacoes] Nenhum hospital ativo no banco '{}'. Carga ignorada.", banco);
            hospitaisAfetados.forEach(indicadorService::recalcular);
            return;
        }

        List<UsuarioDeMassa> usuarios = usuarios(agora);
        usuarioMassa.inserir(usuarios);

        MassaGerada massa = new MassaAvaliacoesGerador(SEMENTE, agora)
                .gerar(hospitais, usuarios.stream().map(UsuarioDeMassa::id).toList());
        visitaMassa.inserir(massa.visitas());
        // Avaliações por último: são elas que marcam a carga como completa.
        mongoTemplate.insertAll(massa.feedbacks());

        hospitaisAfetados.addAll(massa.perfilPorHospital().keySet());
        hospitaisAfetados.forEach(indicadorService::recalcular);

        log.info("[MassaAvaliacoes] Banco '{}': {} usuários, {} visitas e {} avaliações em {} hospitais "
                        + "({} ativos ficaram sem avaliação). Senha dos usuários {}.",
                banco, usuarios.size(), massa.visitas().size(), massa.feedbacks().size(),
                massa.perfilPorHospital().size(), hospitais.size() - massa.perfilPorHospital().size(),
                temSenha() ? "configurada" : "NÃO configurada (sem login)");
    }

    private static Query daMassa() {
        return Query.query(Criteria.where("_id").regex(PREFIXO));
    }

    /** Carga com mais de {@link #VALIDADE_MASSA} (ou sem data) é regerada com datas atuais. */
    private boolean vencida(Instant agora) {
        return usuarioMassa.criadoEm(PRIMEIRO_USUARIO)
                .map(criadoEm -> criadoEm.isBefore(agora.minus(VALIDADE_MASSA)))
                .orElse(true);
    }

    /** Apaga só os documentos da massa e devolve os hospitais cujo indicador precisa ser recalculado. */
    private Set<String> apagarMassa() {
        Query daMassa = daMassa();
        Set<String> hospitais = new HashSet<>(
                mongoTemplate.findDistinct(daMassa, "hospitalId", FeedbackDocument.class, String.class));
        long feedbacks = mongoTemplate.remove(daMassa, FeedbackDocument.class).getDeletedCount();
        long visitas = visitaMassa.apagarComPrefixo(MassaAvaliacoesGerador.PREFIXO_ID);
        long usuarios = usuarioMassa.apagarComPrefixo(MassaAvaliacoesGerador.PREFIXO_ID);
        log.info("[MassaAvaliacoes] Massa anterior removida: {} avaliações, {} visitas e {} usuários.",
                feedbacks, visitas, usuarios);
        return hospitais;
    }

    /**
     * Mantém a senha dos usuários de teste igual à configuração a cada boot. Com senha
     * configurada, ela é aplicada se ainda não estiver valendo (ex.: definida no serviço
     * depois da carga). Sem senha, os usuários recebem uma senha aleatória — tirar a senha
     * da configuração bloqueia o login de novo. Nos dois casos {@code senhaAlteradaEm}
     * avança e os refresh tokens anteriores deixam de valer.
     */
    private void sincronizarSenha(Instant agora) {
        String senha;
        if (temSenha()) {
            if (usuarioMassa.senhaHash(PRIMEIRO_USUARIO)
                    .filter(hash -> passwordEncoder.matches(properties.senha(), hash)).isPresent()) {
                return;
            }
            senha = properties.senha();
        } else {
            senha = UUID.randomUUID().toString();
        }
        long atualizados = usuarioMassa.trocarSenhas(
                MassaAvaliacoesGerador.PREFIXO_ID, passwordEncoder.encode(senha), agora);
        log.info("[MassaAvaliacoes] Senha dos usuários de teste {} ({} usuários).",
                temSenha() ? "aplicada" : "trocada por uma aleatória (sem login)", atualizados);
    }

    private List<UsuarioDeMassa> usuarios(Instant agora) {
        // Um único hash para todos: BCrypt custa ~100 ms por chamada no startup.
        String senhaHash = passwordEncoder.encode(temSenha() ? properties.senha() : UUID.randomUUID().toString());
        List<UsuarioDeMassa> usuarios = new ArrayList<>();
        for (int i = 1; i <= QUANTIDADE_USUARIOS; i++) {
            String numero = "%02d".formatted(i);
            usuarios.add(UsuarioDeMassa.builder()
                    .id(MassaAvaliacoesGerador.PREFIXO_ID + "usuario-" + numero)
                    .nome(NOMES.get((i - 1) % NOMES.size()) + " Teste " + numero)
                    .email("massa.avaliacoes." + numero + "@" + DOMINIO_EMAIL)
                    .senhaHash(senhaHash)
                    .criadoEm(agora)
                    .build());
        }
        return usuarios;
    }

    private boolean temSenha() {
        return properties.senha() != null && !properties.senha().isBlank();
    }
}
