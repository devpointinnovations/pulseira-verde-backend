package br.com.saude_monitor.api.visita.service.impl;

import br.com.saude_monitor.api.config.exception.ValidacaoNegocioException;
import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import br.com.saude_monitor.api.hospital.service.HospitalConsulta;
import br.com.saude_monitor.api.visita.document.OrigemVisita;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.CandidatoGeofence;
import br.com.saude_monitor.api.visita.dto.CheckinRequest;
import br.com.saude_monitor.api.visita.dto.PosicaoDto;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import br.com.saude_monitor.api.visita.service.ConflitoGeofenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Resolução do hospital no check-in por GEOFENCE: um candidato, o mais próximo e o empate (E2-01, E2-04, RN-05). */
class VisitaGeofenceCheckinTest {

    private static final PosicaoDto POSICAO = new PosicaoDto("Point", List.of(-48.0742, -15.9023));

    private VisitaServiceImpl visitaService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        VisitaRepository visitaRepository = mock(VisitaRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        visitaService = new VisitaServiceImpl(visitaRepository,
                new HospitalConsulta(mock(HospitalRepository.class), mongoTemplate), mongoTemplate);
        when(visitaRepository.findFirstByDispositivoIdAndStatusInOrderByEntradaDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(visitaRepository.save(any(VisitaDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private HospitalDocument hospital(String id, double longitude, double latitude) {
        return HospitalDocument.builder().id(id).nome("Hospital " + id).ativo(true)
                .localizacao(new GeoJsonPoint(longitude, latitude)).build();
    }

    private void geofencesQueContemOPonto(HospitalDocument... hospitais) {
        when(mongoTemplate.find(any(Query.class), eq(HospitalDocument.class))).thenReturn(List.of(hospitais));
    }

    private CheckinRequest checkinGeofence(PosicaoDto posicao) {
        return new CheckinRequest(null, OrigemVisita.GEOFENCE, posicao, "anon-1", null);
    }

    @Test
    void checkinPorGeofenceSemPosicaoLancaErroDeNegocio() {
        assertThrows(ValidacaoNegocioException.class, () -> visitaService.checkin(checkinGeofence(null), null));
    }

    @Test
    void checkinPorGeofenceForaDeTodosOsHospitaisLancaErroDeNegocio() {
        geofencesQueContemOPonto();

        assertThrows(ValidacaoNegocioException.class, () -> visitaService.checkin(checkinGeofence(POSICAO), null));
    }

    @Test
    void checkinPorGeofenceComUmSoCandidatoUsaEsseHospital() {
        geofencesQueContemOPonto(hospital("h1", -48.0742, -15.9023));

        assertEquals("h1", visitaService.checkin(checkinGeofence(POSICAO), null).hospitalId());
    }

    @Test
    void checkinPorGeofenceComSobreposicaoUsaOHospitalMaisProximoDoPonto() {
        // h2 está a ~1,1 km do ponto; h1, em cima dele: a diferença passa dos 10 m do empate.
        geofencesQueContemOPonto(hospital("h2", -48.0742, -15.9123), hospital("h1", -48.0742, -15.9023));

        assertEquals("h1", visitaService.checkin(checkinGeofence(POSICAO), null).hospitalId());
    }

    @Test
    void checkinPorGeofenceComEmpateDevolveOsCandidatosOrdenadosPorDistanciaSemCriarVisita() {
        // ~1 m de diferença entre os centroides: dentro do limite de 10 m, o app pergunta ao usuário.
        geofencesQueContemOPonto(hospital("h2", -48.07421, -15.9023), hospital("h1", -48.0742, -15.9023));

        ConflitoGeofenceException conflito = assertThrows(ConflitoGeofenceException.class,
                () -> visitaService.checkin(checkinGeofence(POSICAO), null));

        List<CandidatoGeofence> candidatos = conflito.getCandidatos();
        assertEquals(List.of("h1", "h2"), candidatos.stream().map(CandidatoGeofence::hospitalId).toList());
        assertEquals("Hospital h1", candidatos.getFirst().nome());
        assertTrue(candidatos.getFirst().distanciaMetros() <= candidatos.getLast().distanciaMetros());
    }
}
