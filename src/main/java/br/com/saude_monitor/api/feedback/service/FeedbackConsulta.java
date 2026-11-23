package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Consultas de feedback que outros módulos podem usar; o {@link FeedbackRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class FeedbackConsulta {

    private final FeedbackRepository feedbackRepository;
    private final MongoTemplate mongoTemplate;

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

    public List<FeedbackDocument> findByHospitalIdAndCriadoEmAfterAndNotaNotNull(String hospitalId, Instant criadoEm) {
        return feedbackRepository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull(hospitalId, criadoEm);
    }

    public List<FeedbackDocument> findByCriadoEmAfter(Instant criadoEm) {
        return feedbackRepository.findByCriadoEmAfter(criadoEm);
    }

    public List<FeedbackDocument> findByEditadoEmAfter(Instant editadoEm) {
        return feedbackRepository.findByEditadoEmAfter(editadoEm);
    }
}
