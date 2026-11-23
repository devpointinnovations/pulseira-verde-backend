package br.com.saude_monitor.api.hospital.service;

import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HospitalConsultaTest {

    private final HospitalRepository repository = mock(HospitalRepository.class);
    private final HospitalConsulta consulta = new HospitalConsulta(repository);

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
}
