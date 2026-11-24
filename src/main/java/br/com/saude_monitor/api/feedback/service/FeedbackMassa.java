package br.com.saude_monitor.api.feedback.service;

import br.com.saude_monitor.api.feedback.document.FeedbackDocument;
import br.com.saude_monitor.api.feedback.dto.ResumoDaMassa;
import br.com.saude_monitor.api.feedback.seed.MassaAvaliacoesGerador;
import br.com.saude_monitor.api.feedback.seed.MassaGerada;
import br.com.saude_monitor.api.hospital.dto.HospitalParaMassa;
import br.com.saude_monitor.api.visita.dto.VisitaDeMassa;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Operações de feedback da carga de massa do ambiente de desenvolvimento. Só existe no perfil {@code dev}: o
 * módulo {@code feedback} continua dono do {@link FeedbackDocument}. As avaliações da massa são achadas pelo
 * prefixo do {@code _id}.
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class FeedbackMassa {

    private final MongoTemplate mongoTemplate;

    public boolean existeComPrefixo(String prefixo) {
        return mongoTemplate.exists(comPrefixo(prefixo), FeedbackDocument.class);
    }

    /** Hospitais que têm avaliação da massa; chamar ANTES de {@link #apagarComPrefixo}. */
    public List<String> hospitaisComPrefixo(String prefixo) {
        return mongoTemplate.findDistinct(comPrefixo(prefixo), "hospitalId", FeedbackDocument.class, String.class);
    }

    public long apagarComPrefixo(String prefixo) {
        return mongoTemplate.remove(comPrefixo(prefixo), FeedbackDocument.class).getDeletedCount();
    }

    /**
     * Gera as visitas e as avaliações da massa, entrega as visitas a quem as grava e só então grava as
     * avaliações: são elas que marcam a carga como completa.
     */
    public ResumoDaMassa gerarEGravar(long semente, Instant agora, List<HospitalParaMassa> hospitais,
                                      List<String> usuarioIds, Consumer<List<VisitaDeMassa>> gravarVisitas) {
        MassaGerada massa = new MassaAvaliacoesGerador(semente, agora).gerar(hospitais, usuarioIds);
        gravarVisitas.accept(massa.visitas());
        mongoTemplate.insertAll(massa.feedbacks());
        return ResumoDaMassa.builder()
                .hospitais(new HashSet<>(massa.perfilPorHospital().keySet()))
                .visitas(massa.visitas().size())
                .avaliacoes(massa.feedbacks().size())
                .build();
    }

    private static Query comPrefixo(String prefixo) {
        return Query.query(Criteria.where("_id").regex(Pattern.compile("^" + Pattern.quote(prefixo))));
    }
}
