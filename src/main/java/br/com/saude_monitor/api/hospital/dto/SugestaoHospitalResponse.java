package br.com.saude_monitor.api.hospital.dto;

import br.com.saude_monitor.api.hospital.document.StatusSugestao;

import java.time.Instant;
import lombok.Builder;

/**
 * Resposta da sugestão pública de hospital (E1-05) — criada com status pendente.
 */
@Builder
public record SugestaoHospitalResponse(
        String id,
        String nome,
        EnderecoDto endereco,
        String observacao,
        StatusSugestao status,
        Instant criadoEm
) {
}
