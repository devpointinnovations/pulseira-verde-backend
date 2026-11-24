package br.com.saude_monitor.api.indicador.service.impl;

import br.com.saude_monitor.api.indicador.document.IndicadorHospitalDocument;
import br.com.saude_monitor.api.indicador.dto.TendenciaNota;
import br.com.saude_monitor.api.indicador.repository.IndicadorHospitalRepository;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import br.com.saude_monitor.api.feedback.service.FeedbackConsulta;
import br.com.saude_monitor.api.hospital.dto.IndicadoresResponse;
import br.com.saude_monitor.api.hospital.service.HospitalConsulta;
import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import br.com.saude_monitor.api.visita.service.VisitaConsulta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes do {@link IndicadorServiceImpl} (Épico 04) — regras de agregação RN-14..RN-17,
 * RN-15 (N ≥ 5) e exibição embutida/enriquecida.
 */
class IndicadorServiceImplTest {

    private final IndicadorHospitalRepository indicadorRepository = mock(IndicadorHospitalRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final VisitaRepository visitaRepository = mock(VisitaRepository.class);
    private final HospitalConsulta hospitalConsulta = mock(HospitalConsulta.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final IndicadorService service =
            new IndicadorServiceImpl(indicadorRepository, new FeedbackConsulta(feedbackRepository, mongoTemplate),
                    new VisitaConsulta(visitaRepository, mongoTemplate), hospitalConsulta, mongoTemplate);

    /** Visitas que o stub de {@code findAllById} devolve sem prova de presença (SEG-01). */
    private final Set<String> visitasNaoComprovadas = new HashSet<>();
    /** Visitas que o stub de {@code findAllById} não encontra. */
    private final Set<String> visitasInexistentes = new HashSet<>();
    private int proximaVisita;

    @BeforeEach
    void setup() {
        when(visitaRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<String> ids = inv.getArgument(0);
            List<VisitaDocument> visitas = new ArrayList<>();
            ids.forEach(id -> {
                if (!visitasInexistentes.contains(id)) {
                    visitas.add(VisitaDocument.builder().id(id)
                            .presencaComprovada(!visitasNaoComprovadas.contains(id)).build());
                }
            });
            return visitas;
        });
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any())).thenReturn(List.of());
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any())).thenReturn(List.of());
    }

    private FeedbackDocument feedback(String hosp, int nota) {
        return FeedbackDocument.builder().visitaId("v" + (++proximaVisita)).hospitalId(hosp).nota(nota).build();
    }

    // ------------------------------------------------ RN-14/RN-15 (média + N) ---------------------------------------

    @Test
    void recalcularCalculaNotaMediaENAvaliacoes() {
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(List.of(feedback("h1", 4), feedback("h1", 5), feedback("h1", 3)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getNotaMedia()).isEqualTo(4.0);
        assertThat(indicador.getNAvaliacoes()).isEqualTo(3);
        assertThat(indicador.getHospitalId()).isEqualTo("h1");
        assertThat(indicador.getPeriodoInicio()).isNotNull();
        assertThat(indicador.getPeriodoFim()).isNotNull();
        verify(mongoTemplate).upsert(any(), any(), eq(IndicadorHospitalDocument.class));
    }

    @Test
    void recalcularIgnoraFeedbacksSemNotaDefinida() {
        // Um feedback sem nota (nota null) não conta para média nem N.
        FeedbackDocument semNota = FeedbackDocument.builder().hospitalId("h1").nota(null).build();
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(any(), any()))
                .thenReturn(List.of(feedback("h1", 5), semNota));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        // A query já filtra nota != null; defensivamente o serviço também ignora sem nota.
        assertThat(indicador.getNAvaliacoes()).isEqualTo(1);
        assertThat(indicador.getNotaMedia()).isEqualTo(5.0);
    }

    // ------------------------------------------------ SEG-01 (prova de presença) ------------------------------------

    @Test
    void recalcularIgnoraFeedbackDeVisitaSemProvaDePresenca() {
        FeedbackDocument comprovado = feedback("h1", 4);
        FeedbackDocument semProva = feedback("h1", 1);
        visitasNaoComprovadas.add(semProva.getVisitaId());
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(List.of(comprovado, semProva));

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getNAvaliacoes()).isEqualTo(1);
        assertThat(indicador.getNotaMedia()).isEqualTo(4.0);
    }

    @Test
    void recalcularIgnoraFeedbackSemVisitaEncontrada() {
        FeedbackDocument comprovado = feedback("h1", 5);
        FeedbackDocument orfao = feedback("h1", 1);
        FeedbackDocument semVisita = FeedbackDocument.builder().hospitalId("h1").nota(1).build();
        visitasInexistentes.add(orfao.getVisitaId());
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(List.of(comprovado, orfao, semVisita));

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getNAvaliacoes()).isEqualTo(1);
        assertThat(indicador.getNotaMedia()).isEqualTo(5.0);
    }

    @Test
    void recalcularIgnoraTempoDeVisitaSemProvaDePresenca() {
        VisitaDocument semProva = visitaFinalizada(600);
        semProva.setPresencaComprovada(false);
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaFinalizada(40), semProva));

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getNVisitas()).isEqualTo(1);
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(40);
    }

    // ------------------------------------------------ RN-16 (mediana do tempo) --------------------------------------

    private VisitaDocument visitaFinalizada(int minutos) {
        return VisitaDocument.builder()
                .hospitalId("h1")
                .status(StatusVisita.FINALIZADA)
                .tipoPermanencia(TipoPermanencia.ATENDIMENTO)
                .duracaoMinutos(minutos)
                .presencaComprovada(true)
                .build();
    }

    @Test
    void recalcularCalculaMedianaDoTempo() {
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaFinalizada(30), visitaFinalizada(120), visitaFinalizada(60), visitaFinalizada(90)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(75); // (60+90)/2
        assertThat(indicador.getNVisitas()).isEqualTo(4);
    }

    @Test
    void recalcularExcluiVisitassAcimaDe24h() {
        // 25h (1500min) excede o teto de 24h (RN-16) — sai da métrica.
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaFinalizada(60), visitaFinalizada(1500)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getNVisitas()).isEqualTo(1);
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(60);
    }

    @Test
    void recalcularExcluiVisitasAbaixoDe2Minutos() {
        // RN-07: visitas com menos de 2 minutos são ruído e não entram nas estatísticas públicas.
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaFinalizada(1), visitaFinalizada(2), visitaFinalizada(60)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getNVisitas()).isEqualTo(2);
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(31); // mediana de (2, 60)
    }

    @Test
    void recalcularExcluiVisitasSemDuracao() {
        VisitaDocument semDuracao = VisitaDocument.builder()
                .hospitalId("h1").status(StatusVisita.FINALIZADA)
                .tipoPermanencia(TipoPermanencia.ATENDIMENTO).duracaoMinutos(null).presencaComprovada(true).build();
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaFinalizada(45), semDuracao));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getNVisitas()).isEqualTo(1);
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(45);
    }

    @Test
    void recalcularSemVisitasElegiveisDeixaTempoNulo() {
        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getTempoMedianoMinutos()).isNull();
        assertThat(indicador.getNVisitas()).isZero();
    }

    // ------------------------------------------------ RN-17 (cobertura GPS) ----------------------------------------

    private VisitaDocument visitaGpsInterrompido(Instant entrada, Instant saida, Instant ultimaPosicao) {
        return VisitaDocument.builder()
                .hospitalId("h1")
                .status(StatusVisita.GPS_INTERROMPIDO)
                .tipoPermanencia(TipoPermanencia.ATENDIMENTO)
                .entrada(entrada)
                .saida(saida)
                .ultimaPosicaoEm(ultimaPosicao)
                .duracaoMinutos((int) java.time.Duration.between(entrada, saida).toMinutes())
                .presencaComprovada(true)
                .build();
    }

    @Test
    void visitaGpsInterrompidoEntraComCoberturaSuficiente() {
        Instant entrada = Instant.parse("2026-08-01T10:00:00Z");
        Instant saida = Instant.parse("2026-08-01T11:00:00Z");      // 60min
        Instant posicao = Instant.parse("2026-08-01T10:54:00Z");     // 54/60 = 90% → entra (RN-17)
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaGpsInterrompido(entrada, saida, posicao)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getNVisitas()).isEqualTo(1);
        assertThat(indicador.getTempoMedianoMinutos()).isEqualTo(60);
    }

    @Test
    void visitaGpsInterrompidoSaiComCoberturaInsuficiente() {
        Instant entrada = Instant.parse("2026-08-01T10:00:00Z");
        Instant saida = Instant.parse("2026-08-01T11:00:00Z");      // 60min
        Instant posicao = Instant.parse("2026-08-01T10:20:00Z");     // 20/60 ≈ 33% → NÃO entra (RN-17)
        when(visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                eq("h1"), any(), any(), any(), any()))
                .thenReturn(List.of(visitaGpsInterrompido(entrada, saida, posicao)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");
        assertThat(indicador.getNVisitas()).isZero();
        assertThat(indicador.getTempoMedianoMinutos()).isNull();
    }

    // ------------------------------------------------ RN-15 (exibição N ≥ 5) ----------------------------------------

    @Test
    void mapaIndicadoresOmiteValoresQuandoNAbaixoDe5() {
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.0).nAvaliacoes(3).tempoMedianoMinutos(60).build();
        when(indicadorRepository.findByHospitalIdIn(List.of("h1", "h2")))
                .thenReturn(List.of(a));

        List<IndicadoresResponse> mapa = service.mapaIndicadores(List.of("h1", "h2"));

        assertThat(mapa).hasSize(2);
        assertThat(mapa.get(0).indicadoresDisponiveis()).isFalse();
        assertThat(mapa.get(0).notaMedia()).isNull();
        assertThat(mapa.get(0).tempoMedianoMinutos()).isNull();
        assertThat(mapa.get(0).nAvaliacoes()).isEqualTo(3);
        // h2 sem indicador → indisponível
        assertThat(mapa.get(1).indicadoresDisponiveis()).isFalse();
    }

    @Test
    void mapaIndicadoresExibeValoresQuandoNMaiorIgual5() {
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.2).nAvaliacoes(12).tempoMedianoMinutos(95).build();
        when(indicadorRepository.findByHospitalIdIn(List.of("h1"))).thenReturn(List.of(a));

        IndicadoresResponse res = service.mapaIndicadores(List.of("h1")).getFirst();
        assertThat(res.indicadoresDisponiveis()).isTrue();
        assertThat(res.notaMedia()).isEqualTo(4.2);
        assertThat(res.tempoMedianoMinutos()).isEqualTo(95);
        assertThat(res.nAvaliacoes()).isEqualTo(12);
    }

    // ------------------------------------------------ §3.5 (detalhe enriquecido) -----------------------------------

    @Test
    void obterDetalheRetornaIndisponivelQuandoHospitalNaoExiste() {
        when(hospitalConsulta.existsById("x")).thenReturn(false);
        assertThat(service.obterDetalhe("x")).isNull();
    }

    @Test
    void obterDetalheRetornaPeriodoEVisitas() {
        when(hospitalConsulta.existsById("h1")).thenReturn(true);
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.0).nAvaliacoes(7).tempoMedianoMinutos(80).nVisitas(34)
                .periodoInicio(Instant.parse("2026-05-10T00:00:00Z"))
                .periodoFim(Instant.parse("2026-08-07T23:59:59Z"))
                .build();
        when(indicadorRepository.findByHospitalId("h1")).thenReturn(Optional.of(a));

        var detalhe = service.obterDetalhe("h1");
        assertThat(detalhe.indicadoresDisponiveis()).isTrue();
        assertThat(detalhe.nVisitas()).isEqualTo(34);
        assertThat(detalhe.periodo().inicio()).isEqualTo(Instant.parse("2026-05-10T00:00:00Z"));
        assertThat(detalhe.periodo().fim()).isEqualTo(Instant.parse("2026-08-07T23:59:59Z"));
    }

    // ------------------------------------------------ E4-06 (tendência simples) -----------------------------------

    private FeedbackDocument feedbackHaDias(int nota, int dias) {
        return FeedbackDocument.builder().visitaId("v" + (++proximaVisita)).hospitalId("h1").nota(nota)
                .criadoEm(Instant.now().minus(Duration.ofDays(dias))).build();
    }

    @Test
    void recalcularCalculaEGravaATendenciaDaNota() {
        List<FeedbackDocument> feedbacks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            feedbacks.add(feedbackHaDias(5, 5));   // atual: 5,0 com 6 avaliações
            feedbacks.add(feedbackHaDias(3, 45));  // anterior: 3,0 com 6 avaliações
        }
        feedbacks.add(feedbackHaDias(1, 75));      // fora dos dois períodos, só na nota de 90 dias
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(feedbacks);

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getTendenciaNota()).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(indicador.getVariacaoNota()).isEqualTo(2.0);
        assertThat(indicador.getTendenciaPreliminar()).isTrue();
        assertThat(indicador.getNAvaliacoes()).isEqualTo(13);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(), update.capture(), eq(IndicadorHospitalDocument.class));
        org.bson.Document set = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("tendenciaNota")).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(set.get("variacaoNota")).isEqualTo(2.0);
        assertThat(set.get("tendenciaPreliminar")).isEqualTo(true);
    }

    @Test
    void recalcularIgnoraNaTendenciaFeedbackDeVisitaSemProvaDePresenca() {
        List<FeedbackDocument> feedbacks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            feedbacks.add(feedbackHaDias(4, 5));
            feedbacks.add(feedbackHaDias(4, 45));
        }
        FeedbackDocument semProva = feedbackHaDias(1, 5);
        visitasNaoComprovadas.add(semProva.getVisitaId());
        feedbacks.add(semProva);
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(feedbacks);

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getTendenciaNota()).isEqualTo(TendenciaNota.ESTAVEL);
        assertThat(indicador.getVariacaoNota()).isEqualTo(0.0);
    }

    @Test
    void recalcularSemAvaliacoesNosDoisPeriodosGravaTendenciaNula() {
        when(feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(eq("h1"), any()))
                .thenReturn(List.of(feedbackHaDias(5, 5), feedbackHaDias(4, 45)));

        IndicadorHospitalDocument indicador = service.recalcular("h1");

        assertThat(indicador.getTendenciaNota()).isNull();
        assertThat(indicador.getVariacaoNota()).isNull();
        assertThat(indicador.getTendenciaPreliminar()).isNull();
        // Grava o nulo de propósito: a tendência antiga some quando um período perde avaliações.
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(), update.capture(), eq(IndicadorHospitalDocument.class));
        org.bson.Document set = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsKey("tendenciaNota");
        assertThat(set.get("tendenciaNota")).isNull();
    }

    @Test
    void obterDetalheExpoeATendenciaQuandoOsIndicadoresEstaoDisponiveis() {
        when(hospitalConsulta.existsById("h1")).thenReturn(true);
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.0).nAvaliacoes(12)
                .tendenciaNota(TendenciaNota.CAINDO).variacaoNota(-0.5).tendenciaPreliminar(false)
                .atualizadoEm(Instant.now().minus(Duration.ofDays(29)))
                .build();
        when(indicadorRepository.findByHospitalId("h1")).thenReturn(Optional.of(a));

        var detalhe = service.obterDetalhe("h1");

        assertThat(detalhe.tendenciaNota()).isEqualTo(TendenciaNota.CAINDO);
        assertThat(detalhe.variacaoNota()).isEqualTo(-0.5);
        assertThat(detalhe.tendenciaPreliminar()).isFalse();
    }

    @Test
    void obterDetalheOmiteATendenciaSemRecalculoHaMaisDe30Dias() {
        // Sem recálculo há 31 dias, nenhum feedback entrou no período atual: a tendência gravada
        // descreve períodos que já passaram.
        when(hospitalConsulta.existsById("h1")).thenReturn(true);
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.0).nAvaliacoes(12)
                .tendenciaNota(TendenciaNota.SUBINDO).variacaoNota(0.5).tendenciaPreliminar(false)
                .atualizadoEm(Instant.now().minus(Duration.ofDays(31)))
                .build();
        when(indicadorRepository.findByHospitalId("h1")).thenReturn(Optional.of(a));

        var detalhe = service.obterDetalhe("h1");

        assertThat(detalhe.indicadoresDisponiveis()).isTrue();
        assertThat(detalhe.tendenciaNota()).isNull();
        assertThat(detalhe.variacaoNota()).isNull();
        assertThat(detalhe.tendenciaPreliminar()).isNull();
    }

    @Test
    void tendenciaSemDataDeRecalculoNaoVale() {
        assertThat(IndicadorServiceImpl.tendenciaAtual(null)).isFalse();
        assertThat(IndicadorServiceImpl.tendenciaAtual(Instant.now())).isTrue();
    }

    @Test
    void obterDetalheOmiteATendenciaQuandoNAbaixoDe5() {
        when(hospitalConsulta.existsById("h1")).thenReturn(true);
        IndicadorHospitalDocument a = IndicadorHospitalDocument.builder()
                .hospitalId("h1").notaMedia(4.0).nAvaliacoes(4)
                .tendenciaNota(TendenciaNota.SUBINDO).variacaoNota(1.0).tendenciaPreliminar(true)
                .atualizadoEm(Instant.now())
                .build();
        when(indicadorRepository.findByHospitalId("h1")).thenReturn(Optional.of(a));

        var detalhe = service.obterDetalhe("h1");

        assertThat(detalhe.tendenciaNota()).isNull();
        assertThat(detalhe.variacaoNota()).isNull();
        assertThat(detalhe.tendenciaPreliminar()).isNull();
    }

    // ------------------------------------------------ recalcularPendentes (09/09/2026) -----------------------------

    private VisitaDocument visitaFinalizadaDe(String hospitalId) {
        return VisitaDocument.builder()
                .hospitalId(hospitalId)
                .status(StatusVisita.FINALIZADA)
                .tipoPermanencia(TipoPermanencia.ATENDIMENTO)
                .build();
    }

    /**
     * Antes recalculava os ~340 hospitais ativos a cada execução, mesmo sem nenhuma
     * mudança. Agora só recalcula quem teve feedback ou visita finalizada recente —
     * este teste falha se o full scan voltar (h3, sem atividade, seria recalculado).
     */
    @Test
    void recalcularPendentesRecalculaSoQuemTeveAtividadeRecente() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doReturn(null).when(spyService).recalcular(anyString());

        when(feedbackRepository.findByCriadoEmAfter(any()))
                .thenReturn(List.of(feedback("h1", 4)));
        when(visitaRepository.findByStatusInAndProcessadoEmAfter(any(), any()))
                .thenReturn(List.of(visitaFinalizadaDe("h2")));

        spyService.recalcularPendentes();

        verify(spyService).recalcular("h1");
        verify(spyService).recalcular("h2");
        verify(spyService, never()).recalcular("h3");
        verifyNoInteractions(hospitalConsulta);
    }

    /**
     * Achado do code-review de 09/09/2026: `saida` pode ser retroativa (GPS
     * interrompido grava o último sinal real, não o momento do job; checkout offline
     * sincronizado carrega `ocorridoEm` de um evento antigo) — usá-la como critério de
     * "atividade recente" faria o hospital poder nunca mais ser recalculado. Este teste
     * simula exatamente esse caso: uma visita com `saida` de dias atrás (fora de
     * qualquer janela razoável), mas que a consulta encontrou porque o *pré-filtro real*
     * é por `processadoEm`, não por `saida` — `IndicadorServiceImpl` não deve refiltrar
     * por `saida` em memória.
     */
    @Test
    void recalcularPendentesRecalculaVisitaComSaidaAntigaSeProcessadoEmForRecente() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doReturn(null).when(spyService).recalcular(anyString());

        VisitaDocument gpsInterrompidaHaDias = VisitaDocument.builder()
                .hospitalId("h4")
                .status(StatusVisita.GPS_INTERROMPIDO)
                .saida(Instant.now().minus(java.time.Duration.ofDays(3)))
                .processadoEm(Instant.now())
                .build();
        when(feedbackRepository.findByCriadoEmAfter(any())).thenReturn(List.of());
        when(visitaRepository.findByStatusInAndProcessadoEmAfter(any(), any()))
                .thenReturn(List.of(gpsInterrompidaHaDias));

        spyService.recalcularPendentes();

        verify(spyService).recalcular("h4");
    }

    /**
     * Avaliação editada pelo dono (até 15 dias após criar) muda a nota sem mudar
     * {@code criadoEm}: se o evento se perder, só {@code editadoEm} leva o job ao hospital.
     */
    @Test
    void recalcularPendentesIncluiHospitalDeFeedbackEditado() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doReturn(null).when(spyService).recalcular(anyString());

        when(feedbackRepository.findByCriadoEmAfter(any())).thenReturn(List.of());
        when(feedbackRepository.findByEditadoEmAfter(any())).thenReturn(List.of(feedback("h5", 1)));

        spyService.recalcularPendentes();

        verify(spyService).recalcular("h5");
    }

    @Test
    void recalcularPendentesNaoDuplicaQuandoMesmoHospitalTemFeedbackEVisita() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doReturn(null).when(spyService).recalcular(anyString());

        when(feedbackRepository.findByCriadoEmAfter(any()))
                .thenReturn(List.of(feedback("h1", 4)));
        when(visitaRepository.findByStatusInAndProcessadoEmAfter(any(), any()))
                .thenReturn(List.of(visitaFinalizadaDe("h1")));

        spyService.recalcularPendentes();

        verify(spyService, times(1)).recalcular("h1");
    }

    @Test
    void recalcularPendentesSemAtividadeNaoRecalculaNinguem() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doReturn(null).when(spyService).recalcular(anyString());

        when(feedbackRepository.findByCriadoEmAfter(any())).thenReturn(List.of());
        when(visitaRepository.findByStatusInAndProcessadoEmAfter(any(), any())).thenReturn(List.of());

        spyService.recalcularPendentes();

        verify(spyService, never()).recalcular(anyString());
    }
    /** Falha num hospital não interrompe o lote: os demais candidatos seguem sendo recalculados (RN-18). */
    @Test
    void recalcularPendentesSegueQuandoUmHospitalFalha() {
        IndicadorServiceImpl spyService = spy((IndicadorServiceImpl) service);
        doThrow(new IllegalStateException("Mongo fora")).when(spyService).recalcular("h1");
        doReturn(null).when(spyService).recalcular("h2");

        when(feedbackRepository.findByCriadoEmAfter(any()))
                .thenReturn(List.of(feedback("h1", 4), feedback("h2", 5)));
        when(visitaRepository.findByStatusInAndProcessadoEmAfter(any(), any())).thenReturn(List.of());

        spyService.recalcularPendentes();

        verify(spyService).recalcular("h1");
        verify(spyService).recalcular("h2");
    }
}
