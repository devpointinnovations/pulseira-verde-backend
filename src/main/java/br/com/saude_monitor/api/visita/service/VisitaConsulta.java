package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.VisitaParaAvaliacao;
import br.com.saude_monitor.api.visita.dto.VisitaParaFeedback;
import br.com.saude_monitor.api.visita.dto.VisitaParaIndicador;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Consultas de visita que outros módulos podem usar; o {@link VisitaRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class VisitaConsulta {

    private final VisitaRepository visitaRepository;
    private final MongoTemplate mongoTemplate;

    /** Status das visitas que entram no indicador de tempo: FINALIZADA (RN-03) e GPS_INTERROMPIDO (RN-06). */
    /** Status da visita que já aceita feedback: encerrada, FINALIZADA ou GPS_INTERROMPIDO. */
    private static final List<StatusVisita> STATUS_AVALIAVEIS = List.of(StatusVisita.FINALIZADA, StatusVisita.GPS_INTERROMPIDO);

    /**
     * A visita como o envio de feedback a enxerga: se quem chama é o dono (SEG-02) e se já está
     * encerrada. Vazio quando a visita não existe.
     */
    public Optional<VisitaParaAvaliacao> paraAvaliacao(String visitaId, String usuarioId, String dispositivoId) {
        return visitaRepository.findById(visitaId)
                .map(v -> VisitaParaAvaliacao.builder()
                        .id(v.getId())
                        .hospitalId(v.getHospitalId())
                        .saida(v.getSaida())
                        .avaliavel(STATUS_AVALIAVEIS.contains(v.getStatus()))
                        .dono(VisitaDono.isDonoDaVisita(v, usuarioId, dispositivoId))
                        .build());
    }

    /** Visitas da pessoa encerradas desde o instante, da mais recente para a mais antiga. */
    public List<VisitaParaFeedback> avaliaveisDoUsuarioDesde(String usuarioId, Instant desde) {
        return visitaRepository.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc(usuarioId, STATUS_AVALIAVEIS, desde)
                .stream()
                .map(v -> VisitaParaFeedback.builder()
                        .id(v.getId())
                        .hospitalId(v.getHospitalId())
                        .saida(v.getSaida())
                        .duracaoMinutos(v.getDuracaoMinutos())
                        .build())
                .toList();
    }

    /** Ids das visitas FINALIZADA com saída antes do instante (a janela de feedback já fechou). */
    public List<String> idsFinalizadasAntesDe(Instant limite) {
        return visitaRepository.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, limite).stream()
                .map(VisitaDocument::getId)
                .toList();
    }

    /**
     * Marca como SEM_FEEDBACK as visitas informadas que ainda estão FINALIZADA. Atualiza só o status, em vez
     * de regravar a visita lida: regravar podia devolver conta, aparelho ou pontos de GPS apagados no meio
     * do caminho pela retenção (DAD-01).
     */
    public void marcarSemFeedback(Collection<String> visitaIds) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("_id").in(visitaIds).and("status").is(StatusVisita.FINALIZADA)),
                new Update().set("status", StatusVisita.SEM_FEEDBACK),
                VisitaDocument.class);
    }

    private static final List<StatusVisita> STATUS_TEMPO = List.of(StatusVisita.FINALIZADA, StatusVisita.GPS_INTERROMPIDO);

    /**
     * Visitas do hospital elegíveis ao indicador de tempo: FINALIZADA ou GPS_INTERROMPIDO, com
     * saída no período e tipo de permanência ATENDIMENTO (exclui internação/observação, RN-24).
     */
    public List<VisitaParaIndicador> paraIndicadorDeTempo(String hospitalId, Instant inicio, Instant fim) {
        return visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                        hospitalId, STATUS_TEMPO, inicio, fim, TipoPermanencia.ATENDIMENTO).stream()
                .map(v -> VisitaParaIndicador.builder()
                        .duracaoMinutos(v.getDuracaoMinutos())
                        .comprovada(PresencaVisita.isComprovada(v))
                        .finalizada(v.getStatus() == StatusVisita.FINALIZADA)
                        .gpsInterrompido(v.getStatus() == StatusVisita.GPS_INTERROMPIDO)
                        .entrada(v.getEntrada())
                        .saida(v.getSaida())
                        .ultimaPosicaoEm(v.getUltimaPosicaoEm())
                        .build())
                .toList();
    }

    /** Dos ids informados, os de visitas com prova de presença (SEG-01), lidas em lote. */
    public Set<String> comPresencaComprovada(Collection<String> visitaIds) {
        Set<String> comprovadas = new HashSet<>();
        visitaRepository.findAllById(visitaIds).forEach(v -> {
            if (PresencaVisita.isComprovada(v)) {
                comprovadas.add(v.getId());
            }
        });
        return comprovadas;
    }

    /** Hospitais com visita do indicador de tempo processada desde o instante (`processadoEm`, não `saida`). */
    public Set<String> hospitaisComVisitaProcessadaDesde(Instant desde) {
        Set<String> hospitais = new HashSet<>();
        visitaRepository.findByStatusInAndProcessadoEmAfter(STATUS_TEMPO, desde)
                .forEach(v -> hospitais.add(v.getHospitalId()));
        return hospitais;
    }

    /** Visitas da pessoa, para a exportação de dados (LGPD art. 18). */
    public List<VisitaDocument> doUsuario(String usuarioId) {
        return mongoTemplate.find(porUsuario(usuarioId), VisitaDocument.class);
    }

    /** Hospitais das visitas da pessoa; chamar ANTES de {@link #anonimizarDoUsuario}. */
    public Set<String> hospitaisDoUsuario(String usuarioId) {
        Query query = porUsuario(usuarioId);
        query.fields().include("hospitalId");
        Set<String> ids = new LinkedHashSet<>();
        mongoTemplate.find(query, VisitaDocument.class)
                .forEach(v -> { if (v.getHospitalId() != null) ids.add(v.getHospitalId()); });
        return ids;
    }

    /**
     * Desvincula a pessoa das visitas e apaga o que ainda a identificaria: o
     * {@code dispositivoId} (o mesmo aparelho liga as visitas entre si e a uma pessoa) e os
     * pontos de GPS da visita. Ficam hospital, entrada, saída e duração, que é o que a
     * estatística de tempo usa.
     */
    public void anonimizarDoUsuario(String usuarioId) {
        Update update = new Update().set("usuarioId", null)
                .unset("dispositivoId")
                .unset("pontosAmostrais");
        mongoTemplate.updateMulti(porUsuario(usuarioId), update, VisitaDocument.class);
    }

    private static Query porUsuario(String usuarioId) {
        return Query.query(Criteria.where("usuarioId").is(usuarioId));
    }

}
