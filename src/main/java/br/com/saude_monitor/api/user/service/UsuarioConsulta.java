package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Acesso ao usuário para auth e segurança; o {@link UserRepository} fica privado ao módulo.
 * {@code save} só delega: o auth grava senha e confirmação de e-mail.
 */
@Service
@RequiredArgsConstructor
public class UsuarioConsulta {

    private final UserRepository userRepository;

    public Optional<UserDocument> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public UserDocument save(UserDocument usuario) {
        return userRepository.save(usuario);
    }
}
