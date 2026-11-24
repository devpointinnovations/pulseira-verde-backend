package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.dto.FeedbackParaIndicador;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackConsultaTest {

    private static final Instant DESDE = Instant.parse("2026-11-01T10:00:00Z");

    private final FeedbackRepository repository = mock(FeedbackRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final FeedbackConsulta consulta = new FeedbackConsulta(repository, mongoTemplate);
    private final FeedbackDocument feedback = FeedbackDocument.builder().id("f1").build();

    @Test
    void doUsuarioBuscaOsFeedbacksPeloUsuarioId() {
        when(mongoTemplate.find(any(Query.class), eq(FeedbackDocument.class))).thenReturn(List.of(feedback));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        assertEquals(List.of(feedback), consulta.doUsuario("u1"));

        verify(mongoTemplate).find(query.capture(), eq(FeedbackDocument.class));
        assertEquals("u1", query.getValue().getQueryObject().get("usuarioId"));
    }

    @Test
    void hospitaisDoUsuarioIgnoraFeedbackSemHospital() {
        when(mongoTemplate.find(any(Query.class), eq(FeedbackDocument.class))).thenReturn(List.of(
                FeedbackDocument.builder().hospitalId("h1").build(),
                FeedbackDocument.builder().build()));

        assertEquals(List.of("h1"), List.copyOf(consulta.hospitaisDoUsuario("u1")));
    }

    @Test
    void anonimizarDoUsuarioDesvinculaApagaOComentarioEMantemANota() {
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);

        consulta.anonimizarDoUsuario("u1");

        verify(mongoTemplate).updateMulti(any(Query.class), update.capture(), eq(FeedbackDocument.class));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        Document unset = (Document) update.getValue().getUpdateObject().get("$unset");
        assertEquals(Boolean.TRUE, set.get("anonimizado"));
        assertNull(set.get("usuarioId"));
        assertTrue(unset.containsKey("comentario"));
        assertFalse(unset.containsKey("nota"));
    }

    @Test
    void comNotaDesdeDevolveSoAVisitaANotaEACriacao() {
        Instant criadoEm = DESDE.plusSeconds(60);
        when(repository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull("h1", DESDE)).thenReturn(List.of(
                FeedbackDocument.builder().visitaId("v1").hospitalId("h1").nota(4).comentario("ótimo")
                        .usuarioId("u1").criadoEm(criadoEm).build()));

        assertEquals(List.of(FeedbackParaIndicador.builder().visitaId("v1").nota(4).criadoEm(criadoEm).build()),
                consulta.comNotaDesde("h1", DESDE));
    }

    @Test
    void hospitaisComAtividadeDesdeJuntaFeedbackCriadoEEditado() {
        when(repository.findByCriadoEmAfter(DESDE)).thenReturn(List.of(
                FeedbackDocument.builder().hospitalId("h1").build()));
        when(repository.findByEditadoEmAfter(DESDE)).thenReturn(List.of(
                FeedbackDocument.builder().hospitalId("h2").build(),
                FeedbackDocument.builder().hospitalId("h1").build()));

        assertEquals(Set.of("h1", "h2"), consulta.hospitaisComAtividadeDesde(DESDE));
    }
}
