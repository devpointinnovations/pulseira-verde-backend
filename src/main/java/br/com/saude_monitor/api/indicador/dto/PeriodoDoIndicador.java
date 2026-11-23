package br.com.saude_monitor.api.indicador.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;

/** Intervalo (inclusivo) do período de cálculo (RN-19). */
@Schema(name = "Periodo")
@Builder
public record PeriodoDoIndicador(Instant inicio, Instant fim) {
}
