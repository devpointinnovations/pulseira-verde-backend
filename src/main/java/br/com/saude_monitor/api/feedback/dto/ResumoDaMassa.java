package br.com.saude_monitor.api.feedback.dto;

import lombok.Builder;

import java.util.Set;

/** O que a carga de massa de avaliações gerou: os hospitais que receberam avaliação e as quantidades. */
@Builder
public record ResumoDaMassa(Set<String> hospitais, int visitas, int avaliacoes) {
}
