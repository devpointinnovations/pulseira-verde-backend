package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.dto.HospitalNoPonto;
import br.com.saude_monitor.api.hospital.dto.HospitalResumo;
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

    public Optional<HospitalResumo> resumo(String id) {
        return hospitalRepository.findById(id).map(HospitalConsulta::paraResumo);
    }

    public List<HospitalResumo> resumos(Collection<String> ids) {
        return hospitalRepository.findAllById(ids).stream().map(HospitalConsulta::paraResumo).toList();
    }

    /** Hospitais ativos cujo geofence contém o ponto (E2-01, E2-04). */
    public List<HospitalNoPonto> ativosComGeofenceContendo(GeoJsonPoint ponto) {
        Query query = new Query(Criteria.where("ativo").is(true).and("geofence").intersects(ponto));
        return mongoTemplate.find(query, HospitalDocument.class).stream()
                .map(h -> HospitalNoPonto.builder().id(h.getId()).nome(h.getNome()).localizacao(h.getLocalizacao()).build())
                .toList();
    }

    /** O geofence deste hospital contém o ponto? */
    public boolean geofenceContem(String hospitalId, GeoJsonPoint ponto) {
        return mongoTemplate.exists(
                new Query(Criteria.where("_id").is(hospitalId).and("geofence").intersects(ponto)),
                HospitalDocument.class);
    }

    private static HospitalResumo paraResumo(HospitalDocument h) {
        return HospitalResumo.builder().id(h.getId()).nome(h.getNome()).ativo(h.isAtivo()).build();
    }

    public boolean existsById(String id) {
        return hospitalRepository.existsById(id);
    }

}
