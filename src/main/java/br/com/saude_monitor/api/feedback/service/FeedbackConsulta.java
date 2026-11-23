package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.dto.FeedbackParaIndicador;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Consultas de feedback que outros módulos podem usar; o {@link FeedbackRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class FeedbackConsulta {

    private final FeedbackRepository feedbackRepository;
    private final MongoTemplate mongoTemplate;

    /** Feedbacks do hospital criados desde o instante e com nota. */
    public List<FeedbackParaIndicador> comNotaDesde(String hospitalId, Instant desde) {
        return feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(hospitalId, desde).stream()
                .map(f -> new FeedbackParaIndicador(f.getVisitaId(), f.getNota()))
                .toList();
    }

    /**
     * Hospitais com feedback criado ou editado desde o instante: a edição pelo dono (até 15
     * dias depois de criar) muda a nota de um feedback antigo.
     */
    public Set<String> hospitaisComAtividadeDesde(Instant desde) {
        Set<String> hospitais = new HashSet<>();
        feedbackRepository.findByCriadoEmAfter(desde).forEach(f -> hospitais.add(f.getHospitalId()));
        feedbackRepository.findByEditadoEmAfter(desde).forEach(f -> hospitais.add(f.getHospitalId()));
        return hospitais;
    }

    /** Feedbacks da pessoa, para a exportação de dados (LGPD art. 18). */
    public List<FeedbackDocument> doUsuario(String usuarioId) {
        return mongoTemplate.find(porUsuario(usuarioId), FeedbackDocument.class);
    }

    /** Hospitais dos feedbacks da pessoa; chamar ANTES de {@link #anonimizarDoUsuario}. */
    public Set<String> hospitaisDoUsuario(String usuarioId) {
        Query query = porUsuario(usuarioId);
        query.fields().include("hospitalId");
        Set<String> ids = new LinkedHashSet<>();
        mongoTemplate.find(query, FeedbackDocument.class)
                .forEach(f -> { if (f.getHospitalId() != null) ids.add(f.getHospitalId()); });
        return ids;
    }

    /**
     * Desvincula a pessoa dos feedbacks e marca como anonimizado. O comentário é texto
     * livre e pode trazer nome, telefone ou o caso da pessoa, então sai junto; as respostas
     * fechadas e as notas, que entram nos indicadores, ficam.
     */
    public void anonimizarDoUsuario(String usuarioId) {
        Update update = new Update().set("usuarioId", null).set("anonimizado", true)
                .unset("comentario");
        mongoTemplate.updateMulti(porUsuario(usuarioId), update, FeedbackDocument.class);
    }

    private static Query porUsuario(String usuarioId) {
        return Query.query(Criteria.where("usuarioId").is(usuarioId));
    }
}
