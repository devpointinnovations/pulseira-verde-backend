package br.com.saude_monitor.api.visita.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Troca do hospital de uma visita ativa — {@code PATCH /api/v1/visitas/{id}/hospital}. Só vale
 * para um dos hospitais devolvidos como {@code alternativas} no check-in (geofences sobrepostos,
 * E2-04/RN-05). {@code dispositivoId} é a prova de dono da visita anônima, como no checkout.
 */
public record TrocaHospitalRequest(
        @NotBlank String hospitalId,
        String dispositivoId
) {
}
