package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.dto.ResumoDaMassa;
import br.com.saude_monitor.api.hospital.dto.HospitalParaMassa;
import br.com.saude_monitor.api.visita.dto.VisitaDeMassa;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackMassaTest {

    private static final Instant AGORA = Instant.parse("2026-11-24T10:00:00Z");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final FeedbackMassa massa = new FeedbackMassa(mongoTemplate);

    @Test
    void existeHospitaisEApagarUsamOPrefixoDoId() {
        when(mongoTemplate.exists(any(Query.class), eq(FeedbackDocument.class))).thenReturn(true);
        when(mongoTemplate.findDistinct(any(Query.class), eq("hospitalId"), eq(FeedbackDocument.class), eq(String.class)))
                .thenReturn(List.of("h1", "h2"));
        when(mongoTemplate.remove(any(Query.class), eq(FeedbackDocument.class))).thenReturn(DeleteResult.acknowledged(120));

        assertThat(massa.existeComPrefixo("massa-dev-")).isTrue();
        assertThat(massa.hospitaisComPrefixo("massa-dev-")).containsExactly("h1", "h2");
        assertThat(massa.apagarComPrefixo("massa-dev-")).isEqualTo(120L);
    }

    @Test
    void gerarEGravarEntregaAsVisitasAntesDeGravarAsAvaliacoesEResumeOQueFoiGerado() {
        List<HospitalParaMassa> hospitais = IntStream.range(0, 60)
                .mapToObj(i -> HospitalParaMassa.builder().id("h-%03d".formatted(i)).categoria(i % 3).build()).toList();
        List<List<VisitaDeMassa>> entregues = new ArrayList<>();

        ResumoDaMassa resumo = massa.gerarEGravar(1L, AGORA, hospitais, List.of("u1", "u2"), entregues::add);

        InOrder ordem = inOrder(mongoTemplate);
        ordem.verify(mongoTemplate).insertAll(any(Collection.class));
        assertThat(entregues).hasSize(1);
        assertThat(entregues.getFirst()).hasSize(resumo.visitas()).isNotEmpty();
        assertThat(resumo.avaliacoes()).isPositive();
        assertThat(resumo.hospitais()).isNotEmpty().allMatch(id -> id.startsWith("h-"));
        verify(mongoTemplate).insertAll(any(Collection.class));
    }
}
