package br.com.saude_monitor.api.indicador.dto;

import java.time.Instant;
import lombok.Builder;

/**
 * Resposta do endpoint dedicado {@code GET /api/v1/hospitais/{id}/indicadores} (§3.5).
 *
 * <p>Versão enriquecida dos indicadores públicos: além de nota média / tempo mediano /
 * N, expõe {@code nVisitas} e o período calculado ({@code periodo.inicio/fim}) para
 * transparência total (RN-19).</p>
 *
 * <p>Quando {@code nAvaliacoes < 5} (RN-15), {@code indicadoresDisponiveis = false} e os
 * campos de valor ficam {@code null}.</p>
 */
@Builder
public record IndicadoresDetalheResponse(
        String hospitalId,
        boolean indicadoresDisponiveis,
        Double notaMedia,
        Integer nAvaliacoes,
        Integer tempoMedianoMinutos,
        Integer nVisitas,
        PeriodoDoIndicador periodo,
        Instant atualizadoEm
) {

    /** Fábrica padrão quando não há indicador materializado ainda (nenhum feedback/visita). */
    public static IndicadoresDetalheResponse indisponivel(String hospitalId) {
        return IndicadoresDetalheResponse.builder()
                .hospitalId(hospitalId)
                .indicadoresDisponiveis(false)
                .notaMedia(null)
                .nAvaliacoes(null)
                .tempoMedianoMinutos(null)
                .nVisitas(null)
                .periodo(null)
                .atualizadoEm(null)
                .build();
    }
}
