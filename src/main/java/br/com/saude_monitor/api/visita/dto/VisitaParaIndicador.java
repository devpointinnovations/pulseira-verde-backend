package br.com.saude_monitor.api.visita.dto;

import java.time.Instant;
import lombok.Builder;

/**
 * O que o indicador de tempo precisa de uma visita. {@code comprovada} é a prova de presença
 * (SEG-01); {@code finalizada} e {@code gpsInterrompido} dizem o status.
 */
@Builder
public record VisitaParaIndicador(
        Integer duracaoMinutos,
        boolean comprovada,
        boolean finalizada,
        boolean gpsInterrompido,
        Instant entrada,
        Instant saida,
        Instant ultimaPosicaoEm
) {
}
