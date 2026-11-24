package br.com.saude_monitor.api.user.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * O que o login, a renovação de sessão e a segurança precisam de uma conta. {@code papel} é o nome do
 * papel ({@code USER} quando a conta não tem um); {@code senhaHash} é o BCrypt e nunca sai da API.
 */
@Builder
public record UsuarioAutenticavel(
        String id,
        String email,
        String nomeCompleto,
        String papel,
        String senhaHash,
        boolean ativo,
        boolean emailVerificado,
        Instant senhaAlteradaEm
) {

    /** Sem o hash da senha: um log ou uma mensagem de erro com o objeto não pode vazá-lo. */
    @Override
    public String toString() {
        return "UsuarioAutenticavel[id=" + id + ", papel=" + papel + ", ativo=" + ativo
                + ", emailVerificado=" + emailVerificado + "]";
    }
}
