package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HospitalConsultaTest {

    private final HospitalRepository repository = mock(HospitalRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final HospitalConsulta consulta = new HospitalConsulta(repository, mongoTemplate);
    private final GeoJsonPoint ponto = new GeoJsonPoint(-47.9, -15.8);

    @Test
    void existsByIdDelegaAoRepository() {
        when(repository.existsById("h1")).thenReturn(true);

        assertTrue(consulta.existsById("h1"));
    }

    @Test
    void findByIdDevolveOQueORepositoryDevolve() {
        HospitalDocument hospital = HospitalDocument.builder().id("h1").nome("Hospital Um").build();
        when(repository.findById("h1")).thenReturn(Optional.of(hospital));

        assertEquals(Optional.of(hospital), consulta.findById("h1"));
    }

    @Test
    void findAllByIdRepassaOsIdsEDevolveOResultado() {
        HospitalDocument hospital = HospitalDocument.builder().id("h2").build();
        when(repository.findAllById(List.of("h2", "h3"))).thenReturn(List.of(hospital));

        assertEquals(List.of(hospital), consulta.findAllById(List.of("h2", "h3")));
    }

    @Test
    void resumoDevolveIdNomeEAtivo() {
        when(repository.findById("h1")).thenReturn(
                Optional.of(HospitalDocument.builder().id("h1").nome("Hospital Um").ativo(true).build()));

        assertEquals(Optional.of(new HospitalConsulta.Resumo("h1", "Hospital Um", true)), consulta.resumo("h1"));
        assertEquals(Optional.empty(), consulta.resumo("inexistente"));
    }

    @Test
    void resumosConvertemTodosOsHospitaisEncontrados() {
        when(repository.findAllById(List.of("h1", "h2"))).thenReturn(List.of(
                HospitalDocument.builder().id("h1").nome("Um").ativo(true).build(),
                HospitalDocument.builder().id("h2").nome("Dois").ativo(false).build()));

        assertEquals(
                List.of(new HospitalConsulta.Resumo("h1", "Um", true), new HospitalConsulta.Resumo("h2", "Dois", false)),
                consulta.resumos(List.of("h1", "h2")));
    }

    @Test
    void ativosComGeofenceContendoPedeSoOsAtivosQueInterceptamOPontoEDevolveOCentroide() {
        GeoJsonPoint centro = new GeoJsonPoint(-47.91, -15.81);
        when(mongoTemplate.find(any(Query.class), eq(HospitalDocument.class))).thenReturn(
                List.of(HospitalDocument.builder().id("h1").nome("Um").localizacao(centro).build()));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        List<HospitalConsulta.NoPonto> resultado = consulta.ativosComGeofenceContendo(ponto);

        assertEquals(List.of(new HospitalConsulta.NoPonto("h1", "Um", centro)), resultado);
        verify(mongoTemplate).find(query.capture(), eq(HospitalDocument.class));
        assertEquals(true, query.getValue().getQueryObject().get("ativo"));
        assertTrue(query.getValue().getQueryObject().containsKey("geofence"));
    }

    @Test
    void geofenceContemConsultaOHospitalPeloIdEPeloPonto() {
        when(mongoTemplate.exists(any(Query.class), eq(HospitalDocument.class))).thenReturn(true, false);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        assertTrue(consulta.geofenceContem("h1", ponto));
        assertFalse(consulta.geofenceContem("h2", ponto));

        verify(mongoTemplate, org.mockito.Mockito.times(2)).exists(query.capture(), eq(HospitalDocument.class));
        assertEquals("h1", query.getAllValues().get(0).getQueryObject().get("_id"));
        assertTrue(query.getAllValues().get(0).getQueryObject().containsKey("geofence"));
    }
}
