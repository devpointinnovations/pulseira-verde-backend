package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.config.exception.RecursoNaoEncontradoException;
import br.com.saude_monitor.api.config.security.FonteDeContas;
import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.config.security.UsuarioAutenticavel;
import br.com.saude_monitor.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Acesso à conta para auth e segurança; o {@link UserRepository} e o {@link UserDocument} ficam
 * privados ao módulo {@code user}. As duas escritas são as que o auth faz hoje: senha nova e e-mail confirmado.
 */
@Service
@RequiredArgsConstructor
public class UsuarioConsulta implements FonteDeContas {

    private final UserRepository userRepository;

    /** A conta pelo e-mail (já normalizado), como o login e a segurança a enxergam. */
    @Override
    public Optional<UsuarioAutenticavel> autenticavelPorEmail(String email) {
        return userRepository.findByEmail(email).map(UsuarioConsulta::paraAutenticavel);
    }

    /** Grava a senha nova (hash) e o instante da troca, que encerra as sessões anteriores. */
    public void definirSenha(String email, String senhaHash, Instant alteradaEm) {
        UserDocument conta = contaOuErro(email);
        conta.setSenhaHash(senhaHash);
        conta.setSenhaAlteradaEm(alteradaEm);
        userRepository.save(conta);
    }

    /** Marca o e-mail da conta como confirmado. */
    public void confirmarEmail(String email) {
        UserDocument conta = contaOuErro(email);
        conta.setEmailVerificado(true);
        userRepository.save(conta);
    }

    private UserDocument contaOuErro(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private static UsuarioAutenticavel paraAutenticavel(UserDocument u) {
        return UsuarioAutenticavel.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nomeCompleto(u.getFullName())
                .papel(u.getPapel() == null ? "USER" : u.getPapel().name())
                .senhaHash(u.getSenhaHash())
                .ativo(u.isActive())
                .emailVerificado(u.isEmailVerificado())
                .senhaAlteradaEm(u.getSenhaAlteradaEm())
                .build();
    }
}
