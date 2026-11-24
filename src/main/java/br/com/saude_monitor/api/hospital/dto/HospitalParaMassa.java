package br.com.saude_monitor.api.hospital.dto;

import lombok.Builder;

/**
 * O que a massa de avaliações do ambiente de desenvolvimento precisa de um hospital ativo: o id e a
 * categoria, como a posição dela na lista de categorias (para intercalar hospital, UPA, UBS... sempre na
 * mesma ordem).
 */
@Builder
public record HospitalParaMassa(String id, int categoria) {
}
