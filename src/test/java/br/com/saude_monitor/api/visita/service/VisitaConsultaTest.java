package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitaConsultaTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T10:00:00Z");

    private final VisitaRepository repository = mock(VisitaRepository.class);
    private final VisitaConsulta consulta = new VisitaConsulta(repository);
    private final VisitaDocument visita = VisitaDocument.builder().id("v1").build();

    @Test
    void findByIdEFindAllByIdDelegamAoRepository() {
        when(repository.findById("v1")).thenReturn(Optional.of(visita));
        when(repository.findAllById(List.of("v1"))).thenReturn(List.of(visita));

        assertEquals(Optional.of(visita), consulta.findById("v1"));
        assertEquals(List.of(visita), consulta.findAllById(List.of("v1")));
    }

    @Test
    void consultasPorUsuarioEPorStatusRepassamTodosOsArgumentos() {
        List<StatusVisita> status = List.of(StatusVisita.FINALIZADA);
        when(repository.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc("u1", status, T0))
                .thenReturn(List.of(visita));
        when(repository.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, T1)).thenReturn(List.of(visita));
        when(repository.findByStatusInAndProcessadoEmAfter(status, T0)).thenReturn(List.of(visita));

        assertEquals(List.of(visita),
                consulta.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc("u1", status, T0));
        assertEquals(List.of(visita), consulta.findByStatusAndSaidaBefore(StatusVisita.FINALIZADA, T1));
        assertEquals(List.of(visita), consulta.findByStatusInAndProcessadoEmAfter(status, T0));
    }

    @Test
    void consultaPorHospitalEPeriodoRepassaTodosOsArgumentos() {
        List<StatusVisita> status = List.of(StatusVisita.FINALIZADA);
        when(repository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                "h1", status, T0, T1, TipoPermanencia.ATENDIMENTO)).thenReturn(List.of(visita));

        assertEquals(List.of(visita), consulta.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                "h1", status, T0, T1, TipoPermanencia.ATENDIMENTO));
    }
}
