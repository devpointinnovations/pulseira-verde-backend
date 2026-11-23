package br.com.saude_monitor.api.feedback.dto;

/** O que o indicador do hospital precisa de um feedback: a visita (para a prova de presença) e a nota. */
public record FeedbackParaIndicador(String visitaId, Integer nota) {
}
