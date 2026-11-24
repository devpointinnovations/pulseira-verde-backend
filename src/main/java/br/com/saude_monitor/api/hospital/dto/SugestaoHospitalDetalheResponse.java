package br.com.saude_monitor.api.hospital.dto;

import br.com.saude_monitor.api.hospital.document.StatusSugestao;

import java.time.Instant;
import lombok.Builder;

/**
 * Resposta detalhada de uma sugestão de hospital (E1-06), incluindo audit trail de
 * aprovação ou rejeição administrativa.
 */
@Builder
public record SugestaoHospitalDetalheResponse(
        String id,
        String nome,
        EnderecoDto endereco,
        String observacao,
        StatusSugestao status,
        String hospitalId,
        String revisadoPor,
        Instant revisadoEm,
        String motivoRecusa,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
