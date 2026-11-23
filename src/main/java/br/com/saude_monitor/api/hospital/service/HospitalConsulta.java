package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Consultas de hospital que outros módulos podem usar; o {@link HospitalRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class HospitalConsulta {

    private final HospitalRepository hospitalRepository;
    private final MongoTemplate mongoTemplate;

    /** O que a visita precisa de um hospital: identificar, mostrar o nome e saber se aceita check-in. */
    public record Resumo(String id, String nome, boolean ativo) { }

    /** Hospital ativo cujo geofence contém o ponto, com o centroide para desempate por distância. */
    public record NoPonto(String id, String nome, GeoJsonPoint localizacao) { }

    public Optional<Resumo> resumo(String id) {
        return hospitalRepository.findById(id).map(HospitalConsulta::paraResumo);
    }

    public List<Resumo> resumos(Collection<String> ids) {
        return hospitalRepository.findAllById(ids).stream().map(HospitalConsulta::paraResumo).toList();
    }

    /** Hospitais ativos cujo geofence contém o ponto (E2-01, E2-04). */
    public List<NoPonto> ativosComGeofenceContendo(GeoJsonPoint ponto) {
        Query query = new Query(Criteria.where("ativo").is(true).and("geofence").intersects(ponto));
        return mongoTemplate.find(query, HospitalDocument.class).stream()
                .map(h -> new NoPonto(h.getId(), h.getNome(), h.getLocalizacao()))
                .toList();
    }

    /** O geofence deste hospital contém o ponto? */
    public boolean geofenceContem(String hospitalId, GeoJsonPoint ponto) {
        return mongoTemplate.exists(
                new Query(Criteria.where("_id").is(hospitalId).and("geofence").intersects(ponto)),
                HospitalDocument.class);
    }

    private static Resumo paraResumo(HospitalDocument h) {
        return new Resumo(h.getId(), h.getNome(), h.isAtivo());
    }

    public boolean existsById(String id) {
        return hospitalRepository.existsById(id);
    }

    public Optional<HospitalDocument> findById(String id) {
        return hospitalRepository.findById(id);
    }

    public List<HospitalDocument> findAllById(Collection<String> ids) {
        return hospitalRepository.findAllById(ids);
    }
}
