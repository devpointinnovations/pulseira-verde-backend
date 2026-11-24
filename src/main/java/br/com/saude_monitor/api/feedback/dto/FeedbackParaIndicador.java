package br.com.saude_monitor.api.feedback.dto;

import java.time.Instant;
import lombok.Builder;

/**
 * O que o indicador do hospital precisa de um feedback: a visita (para a prova de presença), a
 * nota e quando foi criado (para separar os períodos da tendência, E4-06).
 */
@Builder
public record FeedbackParaIndicador(String visitaId, Integer nota, Instant criadoEm) {
}
