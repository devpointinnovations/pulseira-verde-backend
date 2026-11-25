package br.com.saude_monitor.api.visita.dto;

import br.com.saude_monitor.api.visita.document.StatusVisita;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Resposta de check-in. {@code criado = true} indica visita nova (HTTP 201); {@code false}
 * indica retorno idempotente de visita ativa já existente no mesmo hospital (HTTP 200, RN-03/§3.3).
 *
 * <p>{@code alternativas} são os outros hospitais cujo geofence também continha a posição do
 * check-in por GEOFENCE (E2-04/RN-05); vazia quando só um hospital continha a posição. O app usa
 * a lista para oferecer a troca ({@code PATCH /api/v1/visitas/{id}/hospital}).</p>
 */
@Builder
public record CheckinResponse(
        String id,
        String hospitalId,
        Instant entrada,
        StatusVisita status,
        boolean criado,
        List<CandidatoGeofence> alternativas
) {
}
