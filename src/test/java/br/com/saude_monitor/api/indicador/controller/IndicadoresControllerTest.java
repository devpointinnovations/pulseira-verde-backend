package br.com.saude_monitor.api.indicador.controller;

import br.com.saude_monitor.api.indicador.dto.IndicadoresDetalheResponse;
import br.com.saude_monitor.api.indicador.dto.PeriodoDoIndicador;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do endpoint público {@code GET /api/v1/hospitais/{id}/indicadores} (§3.5 / Épico 04).
 * Standalone MockMvc, sem MongoDB/Security.
 */
class IndicadoresControllerTest {

    private final IndicadorService indicadorService = mock(IndicadorService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IndicadoresController(indicadorService))
                .build();
    }

    @Test
    void deveRetornarIndicadoresDisponiveis() throws Exception {
        var detalhe = IndicadoresDetalheResponse.builder()
                .hospitalId("hosp-1")
                .indicadoresDisponiveis(true)
                .notaMedia(4.2)
                .nAvaliacoes(12)
                .tempoMedianoMinutos(95)
                .nVisitas(34)
                .periodo(PeriodoDoIndicador.builder()
                        .inicio(Instant.parse("2026-05-10T00:00:00Z"))
                        .fim(Instant.parse("2026-08-07T23:59:59Z"))
                        .build())
                .atualizadoEm(Instant.parse("2026-08-07T16:55:05Z"))
                .build();
        when(indicadorService.obterDetalhe("hosp-1")).thenReturn(detalhe);

        mockMvc.perform(get("/api/v1/hospitais/hosp-1/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalId").value("hosp-1"))
                .andExpect(jsonPath("$.indicadoresDisponiveis").value(true))
                .andExpect(jsonPath("$.notaMedia").value(4.2))
                .andExpect(jsonPath("$.nAvaliacoes").value(12))
                .andExpect(jsonPath("$.tempoMedianoMinutos").value(95))
                .andExpect(jsonPath("$.nVisitas").value(34))
                .andExpect(jsonPath("$.periodo.inicio").value("2026-05-10T00:00:00Z"))
                .andExpect(jsonPath("$.periodo.fim").value("2026-08-07T23:59:59Z"));
    }

    @Test
    void deveRetornarIndisponivelQuandoNAbaixoDe5() throws Exception {
        var detalhe = IndicadoresDetalheResponse.indisponivel("hosp-1");
        when(indicadorService.obterDetalhe("hosp-1")).thenReturn(detalhe);

        mockMvc.perform(get("/api/v1/hospitais/hosp-1/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadoresDisponiveis").value(false))
                .andExpect(jsonPath("$.notaMedia").doesNotExist());
    }

    @Test
    void deveRetornar404QuandoHospitalNaoExistir() throws Exception {
        when(indicadorService.obterDetalhe("inexistente")).thenReturn(null);

        mockMvc.perform(get("/api/v1/hospitais/inexistente/indicadores"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotaDeIndicadoresConvivecomOControllerDeHospitalSemMapeamentoDuplicado() throws Exception {
        when(indicadorService.obterDetalhe("hosp-1")).thenReturn(IndicadoresDetalheResponse.indisponivel("hosp-1"));
        MockMvc comOsDois = MockMvcBuilders.standaloneSetup(
                        new br.com.saude_monitor.api.hospital.controller.HospitalController(
                                mock(br.com.saude_monitor.api.hospital.service.HospitalService.class),
                                new br.com.saude_monitor.api.config.security.AutenticacaoHelper(null),
                                mock(br.com.saude_monitor.api.hospital.service.CatalogoService.class)),
                        new IndicadoresController(indicadorService))
                .build();

        comOsDois.perform(get("/api/v1/hospitais/hosp-1/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadoresDisponiveis").value(false));
    }
}
