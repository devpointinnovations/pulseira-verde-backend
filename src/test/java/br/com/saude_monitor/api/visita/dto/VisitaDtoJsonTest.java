package br.com.saude_monitor.api.visita.dto;

import br.com.saude_monitor.api.visita.document.OrigemVisita;
import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato JSON dos DTOs de visita: o que sai e o que entra tem os mesmos campos, na mesma ordem e com
 * os mesmos nulos, seja o objeto criado pelo construtor ou pelo {@code builder()} do Lombok.
 */
class VisitaDtoJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-23T13:30:00Z");

    private String json(Object objeto) throws Exception {
        return MAPPER.writeValueAsString(objeto);
    }

    @Test
    void checkinResponseSerializaNaOrdemDoContrato() throws Exception {
        CheckinResponse porConstrutor = new CheckinResponse("v1", "h1", T0, StatusVisita.EM_ATENDIMENTO, true);
        CheckinResponse porBuilder = CheckinResponse.builder()
                .id("v1").hospitalId("h1").entrada(T0).status(StatusVisita.EM_ATENDIMENTO).criado(true).build();

        String esperado = "{\"id\":\"v1\",\"hospitalId\":\"h1\",\"entrada\":\"2026-11-23T12:00:00Z\","
                + "\"status\":\"EM_ATENDIMENTO\",\"criado\":true}";
        assertThat(json(porConstrutor)).isEqualTo(esperado);
        assertThat(json(porBuilder)).isEqualTo(esperado);
    }

    @Test
    void checkoutResponseSerializaNaOrdemDoContrato() throws Exception {
        CheckoutResponse porConstrutor = new CheckoutResponse("v1", T1, 90, StatusVisita.FINALIZADA, false);
        CheckoutResponse porBuilder = CheckoutResponse.builder()
                .id("v1").saida(T1).duracaoMinutos(90).status(StatusVisita.FINALIZADA).encerramentoManual(false).build();

        String esperado = "{\"id\":\"v1\",\"saida\":\"2026-11-23T13:30:00Z\",\"duracaoMinutos\":90,"
                + "\"status\":\"FINALIZADA\",\"encerramentoManual\":false}";
        assertThat(json(porConstrutor)).isEqualTo(esperado);
        assertThat(json(porBuilder)).isEqualTo(esperado);
    }

    @Test
    void visitaResponseMantemOsNulosEAOrdemDosCampos() throws Exception {
        VisitaResponse porConstrutor = new VisitaResponse("v1", null, "h1", "Hospital Um", T0, null, null,
                StatusVisita.EM_ATENDIMENTO, TipoPermanencia.ATENDIMENTO, T0, OrigemVisita.MANUAL, T0, false);
        VisitaResponse porBuilder = VisitaResponse.builder()
                .id("v1").hospitalId("h1").hospitalNome("Hospital Um").entrada(T0)
                .status(StatusVisita.EM_ATENDIMENTO).tipoPermanencia(TipoPermanencia.ATENDIMENTO)
                .ultimoHeartbeat(T0).origem(OrigemVisita.MANUAL).criadoEm(T0).visitaValida(false).build();

        String esperado = "{\"id\":\"v1\",\"usuarioId\":null,\"hospitalId\":\"h1\",\"hospitalNome\":\"Hospital Um\","
                + "\"entrada\":\"2026-11-23T12:00:00Z\",\"saida\":null,\"duracaoMinutos\":null,"
                + "\"status\":\"EM_ATENDIMENTO\",\"tipoPermanencia\":\"ATENDIMENTO\","
                + "\"ultimoHeartbeat\":\"2026-11-23T12:00:00Z\",\"origem\":\"MANUAL\","
                + "\"criadoEm\":\"2026-11-23T12:00:00Z\",\"visitaValida\":false}";
        assertThat(json(porConstrutor)).isEqualTo(esperado);
        assertThat(json(porBuilder)).isEqualTo(esperado);
    }

    @Test
    void conflitoDeGeofenceSerializaOsCandidatos() throws Exception {
        CandidatoGeofence candidato = CandidatoGeofence.builder().hospitalId("h1").nome("Um").distanciaMetros(12.5).build();
        ConflitoGeofenceResponse porBuilder = ConflitoGeofenceResponse.builder()
                .timestamp(T0).status(409).code("CONFLITO_GEOFENCE").message("Escolha o hospital.")
                .candidatos(List.of(candidato)).traceId("t-1").build();
        ConflitoGeofenceResponse porConstrutor = new ConflitoGeofenceResponse(
                T0, 409, "CONFLITO_GEOFENCE", "Escolha o hospital.",
                List.of(new CandidatoGeofence("h1", "Um", 12.5)), "t-1");

        String esperado = "{\"timestamp\":\"2026-11-23T12:00:00Z\",\"status\":409,\"code\":\"CONFLITO_GEOFENCE\","
                + "\"message\":\"Escolha o hospital.\","
                + "\"candidatos\":[{\"hospitalId\":\"h1\",\"nome\":\"Um\",\"distanciaMetros\":12.5}],"
                + "\"traceId\":\"t-1\"}";
        assertThat(json(porConstrutor)).isEqualTo(esperado);
        assertThat(json(porBuilder)).isEqualTo(esperado);
    }

    @Test
    void checkinRequestDesserializaComOsMesmosCampos() throws Exception {
        String corpo = "{\"hospitalId\":\"h1\",\"origem\":\"GEOFENCE\","
                + "\"posicao\":{\"type\":\"Point\",\"coordinates\":[-48.0742,-15.9023]},"
                + "\"dispositivoId\":\"anon-1\",\"ocorridoEm\":\"2026-11-23T12:00:00Z\"}";

        CheckinRequest lido = MAPPER.readValue(corpo, CheckinRequest.class);

        assertThat(lido).isEqualTo(CheckinRequest.builder()
                .hospitalId("h1").origem(OrigemVisita.GEOFENCE)
                .posicao(new PosicaoDto("Point", List.of(-48.0742, -15.9023)))
                .dispositivoId("anon-1").ocorridoEm(T0).build());
    }

    @Test
    void checkoutRequestDesserializaComOsMesmosCampos() throws Exception {
        String corpo = "{\"gpsIndisponivel\":true,\"encerramentoManual\":false,"
                + "\"ocorridoEm\":\"2026-11-23T13:30:00Z\",\"dispositivoId\":\"anon-1\"}";

        CheckoutRequest lido = MAPPER.readValue(corpo, CheckoutRequest.class);

        assertThat(lido).isEqualTo(CheckoutRequest.builder()
                .gpsIndisponivel(true).encerramentoManual(false).ocorridoEm(T1).dispositivoId("anon-1").build());
    }
}
