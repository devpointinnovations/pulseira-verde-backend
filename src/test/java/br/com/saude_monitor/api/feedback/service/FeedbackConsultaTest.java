package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackConsultaTest {

    private static final Instant DESDE = Instant.parse("2026-11-01T10:00:00Z");

    private final FeedbackRepository repository = mock(FeedbackRepository.class);
    private final FeedbackConsulta consulta = new FeedbackConsulta(repository);
    private final FeedbackDocument feedback = FeedbackDocument.builder().id("f1").build();

    @Test
    void consultaPorHospitalRepassaHospitalEInstante() {
        when(repository.findByHospitalIdAndCriadoEmAfterAndNotaNotNull("h1", DESDE)).thenReturn(List.of(feedback));

        assertEquals(List.of(feedback), consulta.findByHospitalIdAndCriadoEmAfterAndNotaNotNull("h1", DESDE));
    }

    @Test
    void consultasPorCriacaoEEdicaoDelegamAoRepository() {
        when(repository.findByCriadoEmAfter(DESDE)).thenReturn(List.of(feedback));
        when(repository.findByEditadoEmAfter(DESDE)).thenReturn(List.of());

        assertEquals(List.of(feedback), consulta.findByCriadoEmAfter(DESDE));
        assertEquals(List.of(), consulta.findByEditadoEmAfter(DESDE));
    }
}
