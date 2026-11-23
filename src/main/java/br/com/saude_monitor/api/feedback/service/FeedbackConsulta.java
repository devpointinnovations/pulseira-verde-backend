package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Consultas de feedback que outros módulos podem usar; o {@link FeedbackRepository} fica privado ao módulo. */
@Service
@RequiredArgsConstructor
public class FeedbackConsulta {

    private final FeedbackRepository feedbackRepository;

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
