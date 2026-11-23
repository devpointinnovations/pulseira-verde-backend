package br.com.saude_monitor.api.indicador.service.impl;

import br.com.saude_monitor.api.indicador.document.IndicadorHospitalDocument;
import br.com.saude_monitor.api.indicador.dto.IndicadoresDetalheResponse;
import br.com.saude_monitor.api.indicador.repository.IndicadorHospitalRepository;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import br.com.saude_monitor.api.indicador.service.EstatisticaService;
import br.com.saude_monitor.api.feedback.service.FeedbackConsulta;
import br.com.saude_monitor.api.hospital.dto.IndicadoresResponse;
import br.com.saude_monitor.api.hospital.service.HospitalConsulta;
import br.com.saude_monitor.api.visita.service.VisitaConsulta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementação do serviço de agregação de indicadores públicos (Épico 04 / F-06).
 *
 * <p>Regras aplicadas (RN-14..RN-18):</p>
 * <ul>
 *   <li><b>notaMedia</b> — média aritmética das notas dos feedbacks do hospital nos
 *       últimos 90 dias (RN-14);</li>
 *   <li><b>nAvaliacoes</b> — quantidade de feedbacks na janela; exibir apenas se ≥ 5 (RN-15);</li>
 *   <li><b>tempoMedianoMinutos</b> — mediana das durações de visitas elegíveis na janela,
 *       considerando apenas visitas com duração entre 2min e 24h e com
 *       {@code tipoPermanencia = ATENDIMENTO} (exclui {@code INTERNACAO}/{@code OBSERVACAO} —
 *       RN-16/RN-24); durações < 2min são ruído e ficam de fora (RN-07); visitas
 *       {@code GPS_INTERROMPIDO} entram apenas se a cobertura de GPS ≥ 90% (RN-17);</li>
 *   <li><b>prova de presença</b> — nota e tempo só contam visitas com
 *       {@link PresencaVisita#isComprovada} (SEG-01); a avaliação de quem não comprovou
 *       presença continua aceita, mas fica fora do indicador público;</li>
 *   <li><b>{@code atualizadoEm}</b> — atualização ≤ 15min após feedback, via evento
 *       (RN-18) e job de 15min.</li>
 * </ul>
 *
 * <p>O upsert é idempotente por {@code hospitalId} (índice único) e o mapeamento da
 * listagem/detalhe público é feito em lote ({@code findByHospitalIdIn}) para evitar N+1.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicadorServiceImpl implements IndicadorService {

    /** Janela padrão do período dos indicadores (RN-14): últimos 90 dias. */
    static final Duration PERIODO_PADRAO = Duration.ofDays(90);

    /** Teto de duração para a métrica de tempo (RN-16): visitas de até 24h. */
    static final int TETO_DURACAO_MINUTOS = 24 * 60;

    /** Piso de duração para a métrica de tempo (RN-07): visitas com menos de 2 minutos são ruído e não entram nas estatísticas públicas. */
    static final int DURACAO_MINIMA_MINUTOS = 2;

    private final IndicadorHospitalRepository indicadorRepository;
    private final FeedbackConsulta feedbackConsulta;
    private final VisitaConsulta visitaConsulta;
    private final HospitalConsulta hospitalConsulta;
    private final MongoTemplate mongoTemplate;

    @Override
    public IndicadorHospitalDocument recalcular(String hospitalId) {
        Instant fim = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant inicio = fim.minus(PERIODO_PADRAO);

        // SEG-01: só conta o feedback de visita com prova de presença.
        List<FeedbackConsulta.ParaIndicador> feedbacks = filtrarDeVisitasComprovadas(
                feedbackConsulta.comNotaDesde(hospitalId, inicio));

        List<Integer> notas = feedbacks.stream()
                .map(FeedbackConsulta.ParaIndicador::nota)
                .toList();
        Double notaMedia = EstatisticaService.notaMedia(notas);
        int nAvaliacoes = (int) feedbacks.stream()
                .filter(f -> f.nota() != null && f.nota() >= 1 && f.nota() <= 5)
                .count();

        // Visitas elegíveis para o indicador de tempo (FINALIZADA | GPS_INTERROMPIDO, no período,
        // permanência ATENDIMENTO, RN-24): a visita decide, o indicador aplica as regras de duração.
        List<VisitaConsulta.ParaIndicador> candidatas = visitaConsulta.paraIndicadorDeTempo(hospitalId, inicio, fim);

        List<Integer> duracoes = candidatas.stream()
                .filter(VisitaConsulta.ParaIndicador::comprovada)
                .filter(v -> v.duracaoMinutos() != null)
                .filter(v -> v.duracaoMinutos() >= DURACAO_MINIMA_MINUTOS)
                .filter(v -> v.duracaoMinutos() <= TETO_DURACAO_MINUTOS)
                .filter(IndicadorServiceImpl::tempoConfiavel)
                .map(VisitaConsulta.ParaIndicador::duracaoMinutos)
                .toList();

        Integer tempoMediano = EstatisticaService.tempoMedianoMinutos(duracoes);
        int nVisitas = duracoes.size();

        IndicadorHospitalDocument indicador = IndicadorHospitalDocument.builder()
                .hospitalId(hospitalId)
                .notaMedia(notaMedia)
                .nAvaliacoes(nAvaliacoes)
                .tempoMedianoMinutos(tempoMediano)
                .nVisitas(nVisitas)
                .periodoInicio(inicio)
                .periodoFim(fim)
                .atualizadoEm(fim)
                .build();

        persistir(indicador);
        return indicador;
    }

    @Override
    public List<IndicadoresResponse> mapaIndicadores(Collection<String> hospitalIds) {
        if (hospitalIds == null || hospitalIds.isEmpty()) {
            return List.of();
        }
        Map<String, IndicadorHospitalDocument> porHospital = indicadorRepository
                .findByHospitalIdIn(hospitalIds)
                .stream()
                .collect(Collectors.toMap(IndicadorHospitalDocument::getHospitalId, a -> a));

        return hospitalIds.stream()
                .map(id -> porHospital.containsKey(id)
                        ? toIndicadoresResponse(porHospital.get(id))
                        : IndicadoresResponse.indisponivel())
                .toList();
    }

    @Override
    public IndicadoresDetalheResponse obterDetalhe(String hospitalId) {
        if (!hospitalConsulta.existsById(hospitalId)) {
            return null;
        }
        return indicadorRepository.findByHospitalId(hospitalId)
                .map(this::toDetalheResponse)
                .orElseGet(() -> IndicadoresDetalheResponse.indisponivel(hospitalId));
    }

    /** Margem sobre o ciclo de 15min do job (atraso/jitter de agendamento). */
    static final Duration JANELA_RECALCULO = Duration.ofMinutes(20);

    @Override
    public void recalcularPendentes() {
        // Recalcula só os hospitais com atividade recente (feedback novo ou visita
        // finalizada), não os ~340 hospitais ativos — full scan a cada 15min mesmo sem
        // nenhuma mudança. O evento AFTER_COMMIT (pós-feedback) já cobre o caminho de
        // baixa latência; este job é a rede de segurança para o que ele perder (RN-18).
        // Recalcular um hospital que o evento já recalculou é desperdício pequeno, não um
        // bug — não vale a complexidade extra de tentar excluí-lo daqui.
        Instant desde = Instant.now().minus(JANELA_RECALCULO);

        // Feedback criado ou editado (a edição pelo dono muda a nota de feedback antigo) e visita
        // processada: `processadoEm` (write time), não `saida` (business time, pode ser retroativo —
        // ver VisitaDocument.getProcessadoEm()). Achado do code-review de 09/09/2026.
        Set<String> candidatos = new HashSet<>(feedbackConsulta.hospitaisComAtividadeDesde(desde));
        candidatos.addAll(visitaConsulta.hospitaisComVisitaProcessadaDesde(desde));

        for (String hospitalId : candidatos) {
            try {
                recalcular(hospitalId);
            } catch (Exception ex) {
                log.warn("Falha ao recalcular indicador do hospital {}: {}", hospitalId, ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // RNG / urnas
    // ------------------------------------------------------------------

    /**
     * Visitas FINALIZADA sempre entram (RN-03). Para {@code GPS_INTERROMPIDO} (RN-06),
     * a duração parcial só é confiável se a cobertura de GPS ≥ 90% do período (RN-17).
     */
    private static boolean tempoConfiavel(VisitaConsulta.ParaIndicador visita) {
        if (visita.finalizada()) {
            return true;
        }
        if (!visita.gpsInterrompido()) {
            return false;
        }
        Instant entrada = visita.entrada();
        Instant saida = visita.saida();
        if (entrada == null || saida == null || !saida.isAfter(entrada)) {
            return false;
        }
        double fracao = EstatisticaService.fracaoCoberta(entrada, saida, visita.ultimaPosicaoEm());
        return fracao >= EstatisticaService.PERCENTUAL_MINIMO_COBERTURA_GPS;
    }

    /**
     * Mantém só os feedbacks cuja visita tem prova de presença (SEG-01), lendo as visitas em
     * lote. Feedback sem visita encontrada fica de fora.
     */
    private List<FeedbackConsulta.ParaIndicador> filtrarDeVisitasComprovadas(
            List<FeedbackConsulta.ParaIndicador> feedbacks) {
        List<String> visitaIds = feedbacks.stream()
                .map(FeedbackConsulta.ParaIndicador::visitaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (visitaIds.isEmpty()) {
            return List.of();
        }
        Set<String> comprovadas = visitaConsulta.comPresencaComprovada(visitaIds);
        return feedbacks.stream().filter(f -> comprovadas.contains(f.visitaId())).toList();
    }

    /** Persiste o indicador com upsert idempotente por {@code hospitalId}. */
    private void persistir(IndicadorHospitalDocument indicador) {
        Query query = Query.query(Criteria.where("hospitalId").is(indicador.getHospitalId()));
        Update update = new Update()
                .set("notaMedia", indicador.getNotaMedia())
                .set("nAvaliacoes", indicador.getNAvaliacoes())
                .set("tempoMedianoMinutos", indicador.getTempoMedianoMinutos())
                .set("nVisitas", indicador.getNVisitas())
                .set("periodoInicio", indicador.getPeriodoInicio())
                .set("periodoFim", indicador.getPeriodoFim())
                .set("atualizadoEm", indicador.getAtualizadoEm());

        mongoTemplate.upsert(query, update, IndicadorHospitalDocument.class);
    }

    /** Indicadores embutidos compactos (listagem/detalhe) — omite valores se N < 5 (RN-15). */
    private IndicadoresResponse toIndicadoresResponse(IndicadorHospitalDocument a) {
        boolean disponivel = a.getNAvaliacoes() != null && a.getNAvaliacoes() >= 5;
        return new IndicadoresResponse(
                disponivel,
                disponivel ? a.getNotaMedia() : null,
                a.getNAvaliacoes(),
                disponivel ? a.getTempoMedianoMinutos() : null,
                a.getAtualizadoEm());
    }

    /** Indicadores enriquecidos do endpoint dedicado (§3.5). */
    private IndicadoresDetalheResponse toDetalheResponse(IndicadorHospitalDocument a) {
        boolean disponivel = a.getNAvaliacoes() != null && a.getNAvaliacoes() >= 5;
        var periodo = a.getPeriodoInicio() == null || a.getPeriodoFim() == null
                ? null
                : new IndicadoresDetalheResponse.Periodo(a.getPeriodoInicio(), a.getPeriodoFim());
        return new IndicadoresDetalheResponse(
                a.getHospitalId(),
                disponivel,
                disponivel ? a.getNotaMedia() : null,
                a.getNAvaliacoes(),
                disponivel ? a.getTempoMedianoMinutos() : null,
                a.getNVisitas(),
                periodo,
                a.getAtualizadoEm());
    }
}
