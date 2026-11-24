package br.com.saude_monitor.api.indicador.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicadoresDetalheResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void periodoVaiNoJsonComInicioEFimDentroDoCampoPeriodo() {
        PeriodoDoIndicador periodo = PeriodoDoIndicador.builder()
                .inicio(Instant.parse("2026-08-01T00:00:00Z"))
                .fim(Instant.parse("2026-10-30T00:00:00Z"))
                .build();
        IndicadoresDetalheResponse resposta = IndicadoresDetalheResponse.builder()
                .hospitalId("h1")
                .indicadoresDisponiveis(true)
                .notaMedia(4.5)
                .nAvaliacoes(12)
                .tempoMedianoMinutos(35)
                .nVisitas(40)
                .periodo(periodo)
                .atualizadoEm(Instant.parse("2026-10-30T00:05:00Z"))
                .build();

        JsonNode json = MAPPER.valueToTree(resposta);

        assertEquals("2026-08-01T00:00:00Z", json.get("periodo").get("inicio").asText());
        assertEquals("2026-10-30T00:00:00Z", json.get("periodo").get("fim").asText());
        assertEquals(2, json.get("periodo").size());
    }

    @Test
    void indicadorIndisponivelNaoTemPeriodo() {
        JsonNode json = MAPPER.valueToTree(IndicadoresDetalheResponse.indisponivel("h1"));

        assertTrue(json.get("periodo").isNull());
    }
}
