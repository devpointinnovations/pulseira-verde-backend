package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.user.document.Papel;
import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.user.dto.UsuarioDeMassa;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioMassaTest {

    private static final Instant AGORA = Instant.parse("2026-11-24T10:00:00Z");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UsuarioMassa massa = new UsuarioMassa(mongoTemplate);

    @Test
    void inserirGravaContasComunsAtivasComEmailConfirmadoEAceiteDosTermos() {
        massa.inserir(List.of(UsuarioDeMassa.builder()
                .id("massa-dev-usuario-01").nome("Ana Teste 01").email("a@x.invalid").senhaHash("hash").criadoEm(AGORA).build()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDocument>> gravados = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate).insertAll(gravados.capture());
        UserDocument conta = gravados.getValue().iterator().next();
        assertThat(conta.getId()).isEqualTo("massa-dev-usuario-01");
        assertThat(conta.getPapel()).isEqualTo(Papel.USER);
        assertThat(conta.isActive()).isTrue();
        assertThat(conta.isEmailVerificado()).isTrue();
        assertThat(conta.getConsentimentos().getTermosUso().isAceito()).isTrue();
        assertThat(conta.getConsentimentos().getTermosUso().getData()).isEqualTo(AGORA);
        assertThat(conta.getCreatedAt()).isEqualTo(AGORA);
    }

    @Test
    void criadoEmEsenhaHashVazioQuandoAContaNaoExiste() {
        when(mongoTemplate.findById("x", UserDocument.class)).thenReturn(null);
        when(mongoTemplate.findById("u1", UserDocument.class))
                .thenReturn(UserDocument.builder().id("u1").senhaHash("h").createdAt(AGORA).build());

        assertThat(massa.criadoEm("x")).isEmpty();
        assertThat(massa.senhaHash("x")).isEmpty();
        assertThat(massa.criadoEm("u1")).isEqualTo(Optional.of(AGORA));
        assertThat(massa.senhaHash("u1")).isEqualTo(Optional.of("h"));
    }

    @Test
    void trocarSenhasAtualizaSoAsContasDoPrefixoEDevolveQuantasMudaram() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(UserDocument.class)))
                .thenReturn(UpdateResult.acknowledged(30, 30L, null));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        assertThat(massa.trocarSenhas("massa-dev-", "novo", AGORA)).isEqualTo(30L);

        verify(mongoTemplate).updateMulti(query.capture(), any(UpdateDefinition.class), eq(UserDocument.class));
        assertThat(query.getValue().getQueryObject().get("_id").toString()).contains("massa-dev-");
    }

    @Test
    void existeEApagarUsamOPrefixoDoId() {
        when(mongoTemplate.exists(any(Query.class), eq(UserDocument.class))).thenReturn(true);
        when(mongoTemplate.remove(any(Query.class), eq(UserDocument.class))).thenReturn(DeleteResult.acknowledged(30));

        assertThat(massa.existeComPrefixo("massa-dev-")).isTrue();
        assertThat(massa.apagarComPrefixo("massa-dev-")).isEqualTo(30L);
    }
}
