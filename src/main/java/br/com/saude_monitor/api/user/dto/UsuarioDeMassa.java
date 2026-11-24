package br.com.saude_monitor.api.user.dto;

import lombok.Builder;

import java.time.Instant;

/** Usuário de teste da massa de avaliações do ambiente de desenvolvimento: conta comum, ativa e com e-mail confirmado. */
@Builder
public record UsuarioDeMassa(String id, String nome, String email, String senhaHash, Instant criadoEm) {
}
