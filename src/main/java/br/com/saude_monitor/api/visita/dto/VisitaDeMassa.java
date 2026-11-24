package br.com.saude_monitor.api.visita.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Visita da massa de avaliações do ambiente de desenvolvimento. {@code status}, {@code tipoPermanencia} e
 * {@code origem} são os nomes dos valores do módulo de visita (por exemplo {@code FINALIZADA},
 * {@code ATENDIMENTO}, {@code GEOFENCE}); um nome inválido faz a gravação falhar.
 */
@Builder(toBuilder = true)
public record VisitaDeMassa(
        String id,
        String usuarioId,
        String dispositivoId,
        String hospitalId,
        Instant entrada,
        Instant saida,
        int duracaoMinutos,
        String status,
        String tipoPermanencia,
        String origem,
        boolean presencaComprovada,
        String notas,
        Instant criadoEm,
        Instant ultimoHeartbeat,
        Instant ultimaPosicaoEm,
        Instant processadoEm
) {
}
