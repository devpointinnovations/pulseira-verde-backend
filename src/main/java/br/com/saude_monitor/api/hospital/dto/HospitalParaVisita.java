package br.com.saude_monitor.api.hospital.dto;

import lombok.Builder;

/** O que a visita precisa de um hospital: identificar, mostrar o nome e saber se aceita check-in. */
@Builder
public record HospitalParaVisita(String id, String nome, boolean ativo) {
}
