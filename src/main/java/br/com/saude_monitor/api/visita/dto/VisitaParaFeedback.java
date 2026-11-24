package br.com.saude_monitor.api.visita.dto;

import lombok.Builder;

import java.time.Instant;

/** O que a lista de feedbacks pendentes precisa de uma visita avaliável: hospital, saída e duração. */
@Builder
public record VisitaParaFeedback(String id, String hospitalId, Instant saida, Integer duracaoMinutos) {
}
