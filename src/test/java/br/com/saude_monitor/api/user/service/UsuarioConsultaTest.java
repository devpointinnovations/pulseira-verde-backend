package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.config.exception.RecursoNaoEncontradoException;
import br.com.saude_monitor.api.user.document.Papel;
import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.config.security.UsuarioAutenticavel;
import br.com.saude_monitor.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioConsultaTest {

    private static final Instant TROCA = Instant.parse("2026-11-23T12:00:00Z");

    private final UserRepository repository = mock(UserRepository.class);
    private final UsuarioConsulta consulta = new UsuarioConsulta(repository);

    private UserDocument conta() {
        return UserDocument.builder().id("u1").fullName("Marina Souza").email("marina@email.com")
                .senhaHash("hash").papel(Papel.ADMIN).active(true).emailVerificado(false).senhaAlteradaEm(TROCA).build();
    }

    @Test
    void autenticavelPorEmailCopiaOsCamposQueOLoginPrecisa() {
        when(repository.findByEmail("marina@email.com")).thenReturn(Optional.of(conta()));

        assertEquals(Optional.of(UsuarioAutenticavel.builder().id("u1").email("marina@email.com")
                .nomeCompleto("Marina Souza").papel("ADMIN").senhaHash("hash").ativo(true)
                .emailVerificado(false).senhaAlteradaEm(TROCA).build()), consulta.autenticavelPorEmail("marina@email.com"));
        assertEquals(Optional.empty(), consulta.autenticavelPorEmail("outra@email.com"));
    }

    @Test
    void textoDoObjetoNaoMostraOHashDaSenhaNemOEmail() {
        when(repository.findByEmail("marina@email.com")).thenReturn(Optional.of(conta()));

        String texto = consulta.autenticavelPorEmail("marina@email.com").orElseThrow().toString();

        assertFalse(texto.contains("hash"));
        assertFalse(texto.contains("marina@email.com"));
    }

    @Test
    void contaSemPapelViraUsuarioComum() {
        UserDocument semPapel = conta();
        semPapel.setPapel(null);
        when(repository.findByEmail("marina@email.com")).thenReturn(Optional.of(semPapel));

        assertEquals("USER", consulta.autenticavelPorEmail("marina@email.com").orElseThrow().papel());
    }

    @Test
    void definirSenhaGravaOHashEOInstanteDaTroca() {
        UserDocument conta = conta();
        when(repository.findByEmail("marina@email.com")).thenReturn(Optional.of(conta));
        Instant agora = Instant.parse("2026-11-24T10:00:00Z");

        consulta.definirSenha("marina@email.com", "novo-hash", agora);

        assertEquals("novo-hash", conta.getSenhaHash());
        assertEquals(agora, conta.getSenhaAlteradaEm());
        verify(repository).save(conta);
    }

    @Test
    void confirmarEmailMarcaAContaComoVerificada() {
        UserDocument conta = conta();
        when(repository.findByEmail("marina@email.com")).thenReturn(Optional.of(conta));

        consulta.confirmarEmail("marina@email.com");

        assertTrue(conta.isEmailVerificado());
        verify(repository).save(conta);
    }

    @Test
    void escritaEmContaInexistenteFalhaSemGravar() {
        when(repository.findByEmail("x@email.com")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> consulta.definirSenha("x@email.com", "h", TROCA));
        assertThrows(RecursoNaoEncontradoException.class, () -> consulta.confirmarEmail("x@email.com"));
        verify(repository, never()).save(any(UserDocument.class));
    }
}
