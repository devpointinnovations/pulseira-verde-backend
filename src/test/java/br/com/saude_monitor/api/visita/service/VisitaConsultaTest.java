package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.VisitaParaIndicador;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitaConsultaTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T10:00:00Z");

    private final VisitaRepository repository = mock(VisitaRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final VisitaConsulta consulta = new VisitaConsulta(repository, mongoTemplate);
    private final VisitaDocument visita = VisitaDocument.builder().id("v1").build();

    @Test
    void doUsuarioBuscaAsVisitasPeloUsuarioId() {
        when(mongoTemplate.find(any(Query.class), eq(VisitaDocument.class))).thenReturn(List.of(visita));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        assertEquals(List.of(visita), consulta.doUsuario("u1"));

        verify(mongoTemplate).find(query.capture(), eq(VisitaDocument.class));
        assertEquals("u1", query.getValue().getQueryObject().get("usuarioId"));
    }

    @Test
    void hospitaisDoUsuarioIgnoraVisitaSemHospitalEMantemAOrdem() {
        when(mongoTemplate.find(any(Query.class), eq(VisitaDocument.class))).thenReturn(List.of(
                VisitaDocument.builder().hospitalId("h2").build(),
                VisitaDocument.builder().build(),
                VisitaDocument.builder().hospitalId("h1").build(),
                VisitaDocument.builder().hospitalId("h2").build()));

        assertEquals(List.of("h2", "h1"), List.copyOf(consulta.hospitaisDoUsuario("u1")));
    }

    @Test
    void anonimizarDoUsuarioDesvinculaEApagaAparelhoEPontosDeGps() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);

        consulta.anonimizarDoUsuario("u1");

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(VisitaDocument.class));
        assertEquals("u1", query.getValue().getQueryObject().get("usuarioId"));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        Document unset = (Document) update.getValue().getUpdateObject().get("$unset");
        assertTrue(set.containsKey("usuarioId"));
        assertNull(set.get("usuarioId"));
        assertTrue(unset.containsKey("dispositivoId"));
        assertTrue(unset.containsKey("pontosAmostrais"));
    }

    @Test
    void findByIdDelegaAoRepository() {
        when(repository.findById("v1")).thenReturn(Optional.of(visita));

        assertEquals(Optional.of(visita), consulta.findById("v1"));
    }

    @Test
    void consultasPorUsuarioEPorStatusRepassamTodosOsArgumentos() {
        List<StatusVisita> status = List.of(StatusVisita.FINALIZADA);
        when(repository.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc("u1", status, T0))
                .thenReturn(List.of(visita));
        when(repository.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, T1)).thenReturn(List.of(visita));

        assertEquals(List.of(visita),
                consulta.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc("u1", status, T0));
        assertEquals(List.of(visita), consulta.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, T1));
    }

    @Test
    void paraIndicadorDeTempoPedeSoFinalizadaEGpsInterrompidoDeAtendimentoEConverteOsCampos() {
        VisitaDocument comprovada = VisitaDocument.builder().id("v2").status(StatusVisita.FINALIZADA)
                .duracaoMinutos(30).entrada(T0).saida(T1).presencaComprovada(true).build();
        VisitaDocument semProva = VisitaDocument.builder().id("v3").status(StatusVisita.GPS_INTERROMPIDO)
                .duracaoMinutos(10).entrada(T0).saida(T1).ultimaPosicaoEm(T1).presencaComprovada(false).build();
        when(repository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia("h1",
                List.of(StatusVisita.FINALIZADA, StatusVisita.GPS_INTERROMPIDO), T0, T1, TipoPermanencia.ATENDIMENTO))
                .thenReturn(List.of(comprovada, semProva));

        assertEquals(
                List.of(new VisitaParaIndicador(30, true, true, false, T0, T1, null),
                        new VisitaParaIndicador(10, false, false, true, T0, T1, T1)),
                consulta.paraIndicadorDeTempo("h1", T0, T1));
    }

    @Test
    void comPresencaComprovadaDevolveSoOsIdsComProva() {
        when(repository.findAllById(List.of("v1", "v2"))).thenReturn(List.of(
                VisitaDocument.builder().id("v1").presencaComprovada(true).build(),
                VisitaDocument.builder().id("v2").presencaComprovada(false).build()));

        assertEquals(Set.of("v1"), consulta.comPresencaComprovada(List.of("v1", "v2")));
    }

    @Test
    void hospitaisComVisitaProcessadaDesdeUsaOsStatusDoIndicadorDeTempo() {
        when(repository.findByStatusInAndProcessadoEmAfter(
                List.of(StatusVisita.FINALIZADA, StatusVisita.GPS_INTERROMPIDO), T0))
                .thenReturn(List.of(VisitaDocument.builder().hospitalId("h1").build(),
                        VisitaDocument.builder().hospitalId("h1").build(),
                        VisitaDocument.builder().hospitalId("h2").build()));

        assertEquals(Set.of("h1", "h2"), consulta.hospitaisComVisitaProcessadaDesde(T0));
    }
}
