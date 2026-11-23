package br.com.saude_monitor.api.hospital.dto;

import lombok.Builder;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/** Hospital ativo cujo geofence contém um ponto, com o centroide para o desempate por distância. */
@Builder
public record HospitalNoPonto(String id, String nome, GeoJsonPoint localizacao) {
}
