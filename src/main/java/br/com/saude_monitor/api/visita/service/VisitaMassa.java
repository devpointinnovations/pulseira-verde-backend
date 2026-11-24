package br.com.saude_monitor.api.visita.service;

import br.com.saude_monitor.api.visita.document.OrigemVisita;
import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.TipoPermanencia;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.VisitaDeMassa;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Operações de visita da carga de massa do ambiente de desenvolvimento. Só existe no perfil {@code dev}: o
 * módulo {@code visita} continua dono do {@link VisitaDocument}. As visitas da massa são achadas pelo
 * prefixo do {@code _id}.
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class VisitaMassa {

    private final MongoTemplate mongoTemplate;

    public boolean existeComPrefixo(String prefixo) {
        return mongoTemplate.exists(comPrefixo(prefixo), VisitaDocument.class);
    }

    public void inserir(List<VisitaDeMassa> visitas) {
        mongoTemplate.insertAll(visitas.stream().map(VisitaMassa::paraDocumento).toList());
    }

    public long apagarComPrefixo(String prefixo) {
        return mongoTemplate.remove(comPrefixo(prefixo), VisitaDocument.class).getDeletedCount();
    }

    private static Query comPrefixo(String prefixo) {
        return Query.query(Criteria.where("_id").regex(Pattern.compile("^" + Pattern.quote(prefixo))));
    }

    private static VisitaDocument paraDocumento(VisitaDeMassa v) {
        return VisitaDocument.builder()
                .id(v.id())
                .usuarioId(v.usuarioId())
                .dispositivoId(v.dispositivoId())
                .hospitalId(v.hospitalId())
                .entrada(v.entrada())
                .saida(v.saida())
                .duracaoMinutos(v.duracaoMinutos())
                .status(StatusVisita.valueOf(v.status()))
                .tipoPermanencia(TipoPermanencia.valueOf(v.tipoPermanencia()))
                .origem(OrigemVisita.valueOf(v.origem()))
                .presencaComprovada(v.presencaComprovada())
                .notas(v.notas())
                .criadoEm(v.criadoEm())
                .ultimoHeartbeat(v.ultimoHeartbeat())
                .ultimaPosicaoEm(v.ultimaPosicaoEm())
                .processadoEm(v.processadoEm())
                .build();
    }
}
