package br.com.saude_monitor.api.visita.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * Hospital candidato em caso de empate de geofences sobrepostos (E2-04/RN-05), para o app
 * perguntar em 1 toque "Você está em X ou Y?".
 *
 * <p>Serializável porque viaja dentro de {@code ConflitoGeofenceException}.</p>
 */
@Builder
public record CandidatoGeofence(
        String hospitalId,
        String nome,
        double distanciaMetros
) implements Serializable {
}
