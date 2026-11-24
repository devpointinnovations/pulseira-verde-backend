package br.com.saude_monitor.api.visita.controller;

import br.com.saude_monitor.api.config.exception.GlobalExceptionHandler;
import br.com.saude_monitor.api.visita.dto.CandidatoGeofence;
import br.com.saude_monitor.api.visita.service.ConflitoGeofenceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O empate de geofences continua dando 409 com os candidatos, com o tratador geral registrado junto. */
class VisitaExceptionHandlerTest {

    @RestController
    static class ControllerDeTeste {
        @GetMapping("/empate")
        String empate() {
            throw new ConflitoGeofenceException("Escolha o hospital.",
                    List.of(CandidatoGeofence.builder().hospitalId("h1").nome("Hospital Um").distanciaMetros(3.5).build(), CandidatoGeofence.builder().hospitalId("h2").nome("Hospital Dois").distanciaMetros(4.0).build()));
        }

        @GetMapping("/falha")
        String falha() {
            throw new IllegalStateException("inesperado");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
            .setControllerAdvice(new GlobalExceptionHandler(), new VisitaExceptionHandler())
            .build();

    @Test
    void empateDeGeofenceResponde409ComOsCandidatosEmVezDoErroGenerico() throws Exception {
        mockMvc.perform(get("/empate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLITO_GEOFENCE"))
                .andExpect(jsonPath("$.message").value("Escolha o hospital."))
                .andExpect(jsonPath("$.candidatos[0].hospitalId").value("h1"))
                .andExpect(jsonPath("$.candidatos[1].nome").value("Hospital Dois"));
    }

    @Test
    void outrasFalhasContinuamNoTratadorGeral() throws Exception {
        mockMvc.perform(get("/falha")).andExpect(status().isInternalServerError());
    }
}
