package br.com.saude_monitor.api.user.dto;

import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import br.com.saude_monitor.api.user.document.ConsentimentosDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsentimentosResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Instant ACEITE = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void consentimentoAusenteViraFinalidadeNaoAceitaSemDataNemVersao() {
        FinalidadeResponse vazia = FinalidadeResponse.de(null);

        assertFalse(vazia.aceito());
        assertEquals(null, vazia.data());
        assertEquals(null, vazia.versao());
    }

    @Test
    void finalidadeCopiaAceiteDataEVersaoDoConsentimento() {
        FinalidadeResponse finalidade = FinalidadeResponse.de(new ConsentimentoItem(true, ACEITE, "1.0"));

        assertEquals(FinalidadeResponse.builder().aceito(true).data(ACEITE).versao("1.0").build(), finalidade);
    }

    @Test
    void jsonDasFinalidadesContinuaComOsMesmosCampos() throws Exception {
        ConsentimentosDocument documento = ConsentimentosDocument.builder()
                .localizacao(new ConsentimentoItem(true, ACEITE, "1.0"))
                .build();

        JsonNode json = MAPPER.valueToTree(ConsentimentosResponse.de(documento));

        assertTrue(json.get("localizacao").get("aceito").asBoolean());
        assertEquals("2026-08-01T12:00:00Z", json.get("localizacao").get("data").asText());
        assertEquals("1.0", json.get("localizacao").get("versao").asText());
        assertFalse(json.get("notificacoes").get("aceito").asBoolean());
        assertEquals(3, json.size());
    }
}
