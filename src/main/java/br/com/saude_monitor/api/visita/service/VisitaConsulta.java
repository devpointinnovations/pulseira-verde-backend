package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.repository.VisitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Consultas de visita que outros módulos podem usar; o {@link VisitaRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class VisitaConsulta {

    private final VisitaRepository visitaRepository;

    public Optional<VisitaDocument> findById(String id) {
        return visitaRepository.findById(id);
    }

    public List<VisitaDocument> findAllById(Collection<String> ids) {
        return visitaRepository.findAllById(ids);
    }

    public List<VisitaDocument> findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc(
            String usuarioId, List<StatusVisita> status, Instant limite) {
        return visitaRepository.findByUsuarioIdAndStatusInAndSaidaAfterOrderBySaidaDesc(usuarioId, status, limite);
    }

    public List<VisitaDocument> findByStatusAndSaidaBefore(StatusVisita status, Instant limite) {
        return visitaRepository.findByStatusAndSaidaBefore(status, limite);
    }

    public List<VisitaDocument> findByStatusInAndProcessadoEmAfter(List<StatusVisita> status, Instant limite) {
        return visitaRepository.findByStatusInAndProcessadoEmAfter(status, limite);
    }

    public List<VisitaDocument> findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
            String hospitalId, List<StatusVisita> status, Instant inicio, Instant fim,
            TipoPermanencia tipoPermanencia) {
        return visitaRepository.findByHospitalIdAndStatusInAndSaidaBetweenAndTipoPermanencia(
                hospitalId, status, inicio, fim, tipoPermanencia);
    }
}
