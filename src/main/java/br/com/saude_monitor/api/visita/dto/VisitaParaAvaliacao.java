package br.com.saude_monitor.api.visita.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * O que o envio de um feedback precisa de uma visita. {@code dono} diz se quem chama é a pessoa
 * (ou o aparelho, na visita anônima) que fez a visita (SEG-02); {@code avaliavel}, se a visita já
 * foi encerrada (FINALIZADA ou GPS_INTERROMPIDO).
 */
@Builder
public record VisitaParaAvaliacao(String id, String hospitalId, Instant saida, boolean avaliavel, boolean dono) {
}
