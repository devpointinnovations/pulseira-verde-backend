package br.com.saude_monitor.api.hospital.controller;

import br.com.saude_monitor.api.hospital.document.TipoEstabelecimento;
import br.com.saude_monitor.api.hospital.dto.CatalogoResponse;
import br.com.saude_monitor.api.hospital.dto.HospitalResumoResponse;
import br.com.saude_monitor.api.hospital.dto.IndicadoresCatalogoResponse;
import br.com.saude_monitor.api.hospital.dto.IndicadoresResponse;
import br.com.saude_monitor.api.hospital.service.CatalogoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/v1/catalogo} com cache HTTP (ARQ-01). */
class CatalogoControllerTest {

    private static final String VERSAO = "0123456789abcdef0123456789abcdef";
    private static final String VERSAO_INDICADORES = "fedcba9876543210fedcba9876543210";

    private final CatalogoService catalogoService = mock(CatalogoService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        HospitalResumoResponse item = HospitalResumoResponse.builder()
                .id("h1")
                .nome("Hospital Um")
                .tipo(TipoEstabelecimento.PUBLICO)
                .categoria(null)
                .tipoUnidade(null)
                .endereco(null)
                .localizacao(null)
                .raioMetros(150)
                .ativo(true)
                .regiaoAdministrativa(null)
                .indicadores(null)
                .build();
        when(catalogoService.obterCatalogo()).thenReturn(CatalogoResponse.builder().versao(VERSAO).total(1).estabelecimentos(List.of(item)).build());
        when(catalogoService.obterIndicadores()).thenReturn(new IndicadoresCatalogoResponse(VERSAO_INDICADORES,
                Map.of("h1", IndicadoresResponse.builder()
                        .indicadoresDisponiveis(true)
                        .notaMedia(4.5)
                        .nAvaliacoes(12)
                        .tempoMedianoMinutos(40)
                        .atualizadoEm(null)
                        .build())));
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogoController(catalogoService)).build();
    }

    @Test
    void devolveOCatalogoComETagECacheControlPublico() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"" + VERSAO + "\""))
                .andExpect(header().string("Cache-Control", "max-age=60, public, s-maxage=300"))
                .andExpect(jsonPath("$.versao").value(VERSAO))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.estabelecimentos[0].id").value("h1"))
                .andExpect(jsonPath("$.estabelecimentos[0].indicadores").doesNotExist());
    }

    @Test
    void devolve304SemCorpoQuandoOAppJaTemEstaVersao() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo").header("If-None-Match", "W/\"" + VERSAO + "\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "W/\"" + VERSAO + "\""))
                .andExpect(content().string(""));
    }

    @Test
    void devolveOCatalogoQuandoAVersaoDoAppEstaVelha() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo").header("If-None-Match", "W/\"versao-antiga\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void devolveOsIndicadoresDeTodoOCatalogoComETagProprio() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo/indicadores"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"" + VERSAO_INDICADORES + "\""))
                .andExpect(header().string("Cache-Control", "max-age=60, public, s-maxage=300"))
                .andExpect(jsonPath("$.versao").value(VERSAO_INDICADORES))
                .andExpect(jsonPath("$.porHospital.h1.notaMedia").value(4.5))
                .andExpect(jsonPath("$.porHospital.h1.nAvaliacoes").value(12));
    }

    @Test
    void indicadoresRespondem304QuandoNadaMudou() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo/indicadores")
                        .header("If-None-Match", "W/\"" + VERSAO_INDICADORES + "\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }
}
