package br.com.saude_monitor.api.config.security;

import br.com.saude_monitor.api.user.dto.UsuarioAutenticavel;
import br.com.saude_monitor.api.user.service.UsuarioConsulta;
import br.com.saude_monitor.api.user.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário pelo e-mail para a autenticação JWT (F0-01).
 *
 * <p>Converte {@link UsuarioAutenticavel} em {@link UserDetails}, usando o {@code senhaHash}
 * (BCrypt) e o papel como autoridade {@code ROLE_<PAPEL>}. Usuários inativos são
 * marcados como {@code disabled}, bloqueando o acesso mesmo com token válido.</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioConsulta usuarioConsulta;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizado = EmailNormalizer.normalizar(email);
        if (normalizado == null) {
            // Contrato do UserDetailsService: username ausente é "não encontrado", não
            // uma exceção de acesso a dados — evita repassar null a uma query derivada
            // do Spring Data (comportamento não garantido pelo driver do Mongo).
            throw new UsernameNotFoundException("Usuário não encontrado: " + email);
        }
        UsuarioAutenticavel user = usuarioConsulta.autenticavelPorEmail(normalizado)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));

        return User.withUsername(user.email())
                .password(user.senhaHash() == null ? "" : user.senhaHash())
                .roles(user.papel())
                .disabled(!user.ativo())
                .build();
    }
}
