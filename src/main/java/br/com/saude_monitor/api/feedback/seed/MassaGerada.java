package br.com.saude_monitor.api.feedback.seed;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.visita.dto.VisitaDeMassa;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/** Resultado da geração: o que gravar e o perfil sorteado para cada hospital. */
@Builder
public record MassaGerada(
        List<VisitaDeMassa> visitas,
        List<FeedbackDocument> feedbacks,
        Map<String, PerfilDoHospital> perfilPorHospital) {
}
