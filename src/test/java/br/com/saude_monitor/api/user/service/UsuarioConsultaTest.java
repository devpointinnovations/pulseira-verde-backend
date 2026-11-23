package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioConsultaTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UsuarioConsulta consulta = new UsuarioConsulta(repository);
    private final UserDocument usuario = new UserDocument();

    @Test
    void findByEmailRepassaOEmailSemAlterar() {
        when(repository.findByEmail("a@b.com")).thenReturn(Optional.of(usuario));

        assertEquals(Optional.of(usuario), consulta.findByEmail("a@b.com"));
    }

    @Test
    void saveDevolveOQueORepositoryGravou() {
        when(repository.save(usuario)).thenReturn(usuario);

        assertSame(usuario, consulta.save(usuario));
    }
}
