package br.com.saude_monitor.api.hospital.dto;

import lombok.Builder;

/** O que outros módulos precisam de um hospital: identificar, mostrar o nome e saber se aceita check-in. */
@Builder
public record HospitalResumo(String id, String nome, boolean ativo) {
}
