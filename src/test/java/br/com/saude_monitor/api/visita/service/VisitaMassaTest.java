package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.OrigemVisita;
import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.VisitaDeMassa;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitaMassaTest {

    private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-20T11:00:00Z");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final VisitaMassa massa = new VisitaMassa(mongoTemplate);

    private VisitaDeMassa visita(String status) {
        return VisitaDeMassa.builder().id("massa-dev-visita-00001").usuarioId("u1").hospitalId("h1")
                .entrada(T0).saida(T1).duracaoMinutos(60).status(status).tipoPermanencia("INTERNACAO")
                .origem("MANUAL").presencaComprovada(true).notas("massa").criadoEm(T0)
                .ultimoHeartbeat(T1).ultimaPosicaoEm(T1).processadoEm(T1).build();
    }

    @Test
    void inserirConverteOsNomesDosValoresEGravaTodosOsCampos() {
        massa.inserir(List.of(visita("SEM_FEEDBACK")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<VisitaDocument>> gravadas = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate).insertAll(gravadas.capture());
        VisitaDocument v = gravadas.getValue().iterator().next();
        assertThat(v.getStatus()).isEqualTo(StatusVisita.SEM_FEEDBACK);
        assertThat(v.getTipoPermanencia()).isEqualTo(TipoPermanencia.INTERNACAO);
        assertThat(v.getOrigem()).isEqualTo(OrigemVisita.MANUAL);
        assertThat(v.getDuracaoMinutos()).isEqualTo(60);
        assertThat(v.getPresencaComprovada()).isTrue();
        assertThat(v.getProcessadoEm()).isEqualTo(T1);
    }

    @Test
    void nomeDeValorInvalidoFazAGravacaoFalharEmVezDeGravarLixo() {
        assertThatThrownBy(() -> massa.inserir(List.of(visita("INEXISTENTE"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existeEApagarUsamOPrefixoDoId() {
        when(mongoTemplate.exists(any(Query.class), eq(VisitaDocument.class))).thenReturn(true);
        when(mongoTemplate.remove(any(Query.class), eq(VisitaDocument.class))).thenReturn(DeleteResult.acknowledged(85));

        assertThat(massa.existeComPrefixo("massa-dev-")).isTrue();
        assertThat(massa.apagarComPrefixo("massa-dev-")).isEqualTo(85L);
    }
}
