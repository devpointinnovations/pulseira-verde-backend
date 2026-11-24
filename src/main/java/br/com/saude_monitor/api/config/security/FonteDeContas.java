package br.com.saude_monitor.api.config.security;

import java.util.Optional;

/**
 * De onde a segurança lê a conta de quem está autenticando. O módulo {@code user} implementa; assim a
 * segurança não depende de nenhum módulo de negócio.
 */
public interface FonteDeContas {

    /** A conta pelo e-mail (já normalizado), vazio se não existe. */
    Optional<UsuarioAutenticavel> autenticavelPorEmail(String email);
}
