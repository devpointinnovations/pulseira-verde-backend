package br.com.saude_monitor.api.feedback.dto;

import lombok.Builder;

/** O que o indicador do hospital precisa de um feedback: a visita (para a prova de presença) e a nota. */
@Builder
public record FeedbackParaIndicador(String visitaId, Integer nota) {
}
