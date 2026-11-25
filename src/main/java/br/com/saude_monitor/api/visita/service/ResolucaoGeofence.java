package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.dto.CandidatoGeofence;

import java.util.List;

/**
 * Hospital escolhido para um check-in por GEOFENCE e os outros cujo geofence também continha a
 * posição, do mais próximo ao mais distante (E2-04/RN-05).
 */
public record ResolucaoGeofence(String hospitalId, List<CandidatoGeofence> alternativas) {
}
