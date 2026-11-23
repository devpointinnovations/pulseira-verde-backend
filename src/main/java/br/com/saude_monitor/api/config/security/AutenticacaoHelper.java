package br.com.saude_monitor.api.config.security;

import br.com.saude_monitor.api.user.service.UsuarioConsulta;
import br.com.saude_monitor.api.user.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper para obter informações do usuário autenticado no contexto de segurança.
 */
@Component
@RequiredArgsConstructor
public class AutenticacaoHelper {

    private final UsuarioConsulta usuarioConsulta;

    public Optional<String> usuarioIdAtual() {
        return usuarioEmailAtual()
                .flatMap(email -> usuarioConsulta.findByEmail(EmailNormalizer.normalizar(email)))
                .map(u -> u.getId());
    }

    public Optional<String> usuarioEmailAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        return switch (principal) {
            case UserDetails userDetails -> Optional.ofNullable(userDetails.getUsername());
            case null -> Optional.empty();
            default -> Optional.empty();
        };
    }
}
