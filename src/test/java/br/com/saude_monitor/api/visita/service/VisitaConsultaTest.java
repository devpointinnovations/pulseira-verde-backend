package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.VisitaParaAvaliacao;
import br.com.saude_monitor.api.visita.dto.VisitaParaFeedback;
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
    void paraAvaliacaoDizSeQuemChamaEDonoESeAVisitaJaFoiEncerrada() {
        when(repository.findById("v1")).thenReturn(Optional.of(VisitaDocument.builder().id("v1")
                .usuarioId("u1").hospitalId("h1").status(StatusVisita.FINALIZADA).saida(T1).build()));
        when(repository.findById("v2")).thenReturn(Optional.of(VisitaDocument.builder().id("v2")
                .dispositivoId("anon-1").status(StatusVisita.EM_ATENDIMENTO).build()));

        assertEquals(Optional.of(VisitaParaAvaliacao.builder().id("v1").hospitalId("h1").saida(T1)
                .avaliavel(true).dono(true).build()), consulta.paraAvaliacao("v1", "u1", null));
        assertEquals(false, consulta.paraAvaliacao("v1", "outro", null).orElseThrow().dono());
        VisitaParaAvaliacao emAndamento = consulta.paraAvaliacao("v2", null, "anon-1").orElseThrow();
        assertEquals(true, emAndamento.dono());
        assertEquals(false, emAndamento.avaliavel());
        assertEquals(Optional.empty(), consulta.paraAvaliacao("inexistente", "u1", null));
    }

    @Test
    void paraAvaliacaoDeVisitaAnonimaSoVaiParaOAparelhoDono() {
        when(repository.findById("v1")).thenReturn(Optional.of(VisitaDocument.builder().id("v1")
                .dispositivoId("anon-1").status(StatusVisita.FINALIZADA).build()));

        assertEquals(true, consulta.paraAvaliacao("v1", null, "anon-1").orElseThrow().dono());
        assertEquals(false, consulta.paraAvaliacao("v1", null, "anon-2").orElseThrow().dono());
        assertEquals(false, consulta.paraAvaliacao("v1", null, null).orElseThrow().dono());
        assertEquals(false, consulta.paraAvaliacao("v1", "u1", null).orElseThrow().dono());
    }

    @Test
    void paraAvaliacaoTrataGpsInterrompidoComoVisitaEncerrada() {
        when(repository.findById("v1")).thenReturn(Optional.of(VisitaDocument.builder().id("v1")
                .usuarioId("u1").status(StatusVisita.GPS_INTERROMPIDO).build()));
        when(repository.findById("v2")).thenReturn(Optional.of(VisitaDocument.builder().id("v2")
                .usuarioId("u1").status(StatusVisita.SUSPEITA).build()));

        assertEquals(true, consulta.paraAvaliacao("v1", "u1", null).orElseThrow().avaliavel());
        assertEquals(false, consulta.paraAvaliacao("v2", "u1", null).orElseThrow().avaliavel());
    }

    @Test
    void avaliaveisDoUsuarioDesdePedeSoFinalizadaEGpsInterrompidoEConverteOsCampos() {
        when(repository.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc("u1",
                List.of(StatusVisita.FINALIZADA, StatusVisita.GPS_INTERROMPIDO), T0))
                .thenReturn(List.of(VisitaDocument.builder().id("v1").hospitalId("h1").saida(T1)
                        .duracaoMinutos(25).usuarioId("u1").build()));

        assertEquals(List.of(VisitaParaFeedback.builder().id("v1").hospitalId("h1").saida(T1).duracaoMinutos(25).build()),
                consulta.avaliaveisDoUsuarioDesde("u1", T0));
    }

    @Test
    void idsFinalizadasAntesDeDevolveSoOsIds() {
        when(repository.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, T1)).thenReturn(List.of(
                VisitaDocument.builder().id("v1").build(), VisitaDocument.builder().id("v2").build()));

        assertEquals(List.of("v1", "v2"), consulta.idsFinalizadasAntesDe(T1));
    }

    @Test
    void marcarSemFeedbackAtualizaSoOStatusDasQueAindaEstaoFinalizadas() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);

        consulta.marcarSemFeedback(List.of("v1", "v2"));

        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(VisitaDocument.class));
        assertEquals(StatusVisita.FINALIZADA, query.getValue().getQueryObject().get("status"));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertEquals(StatusVisita.SEM_FEEDBACK, set.get("status"));
        assertEquals(1, set.size());
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
                List.of(VisitaParaIndicador.builder()
                        .duracaoMinutos(30)
                        .comprovada(true)
                        .finalizada(true)
                        .gpsInterrompido(false)
                        .entrada(T0)
                        .saida(T1)
                        .ultimaPosicaoEm(null)
                        .build(),
                        VisitaParaIndicador.builder()
                                .duracaoMinutos(10)
                                .comprovada(false)
                                .finalizada(false)
                                .gpsInterrompido(true)
                                .entrada(T0)
                                .saida(T1)
                                .ultimaPosicaoEm(T1)
                                .build()),
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
