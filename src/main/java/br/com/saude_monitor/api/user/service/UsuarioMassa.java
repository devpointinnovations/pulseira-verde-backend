package br.com.saude_monitor.api.user.service;

import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import br.com.saude_monitor.api.user.document.ConsentimentosDocument;
import br.com.saude_monitor.api.user.document.Papel;
import br.com.saude_monitor.api.user.document.UserDocument;
import br.com.saude_monitor.api.user.dto.UsuarioDeMassa;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Operações de usuário da carga de massa do ambiente de desenvolvimento. Só existe no perfil {@code dev}:
 * o módulo {@code user} continua dono do {@link UserDocument}. As contas da massa são achadas pelo
 * prefixo do {@code _id}.
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class UsuarioMassa {

    private final MongoTemplate mongoTemplate;

    public boolean existeComPrefixo(String prefixo) {
        return mongoTemplate.exists(comPrefixo(prefixo), UserDocument.class);
    }

    /** Quando a conta foi criada; vazio se ela não existe ou não tem a data. */
    public Optional<Instant> criadoEm(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, UserDocument.class)).map(UserDocument::getCreatedAt);
    }

    public Optional<String> senhaHash(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, UserDocument.class)).map(UserDocument::getSenhaHash);
    }

    /** Troca a senha de todas as contas do prefixo; devolve quantas mudaram. */
    public long trocarSenhas(String prefixo, String senhaHash, Instant alteradaEm) {
        return mongoTemplate.updateMulti(comPrefixo(prefixo),
                        new Update().set("senhaHash", senhaHash).set("senhaAlteradaEm", alteradaEm),
                        UserDocument.class)
                .getModifiedCount();
    }

    public void inserir(List<UsuarioDeMassa> usuarios) {
        mongoTemplate.insertAll(usuarios.stream().map(UsuarioMassa::paraDocumento).toList());
    }

    public long apagarComPrefixo(String prefixo) {
        return mongoTemplate.remove(comPrefixo(prefixo), UserDocument.class).getDeletedCount();
    }

    private static Query comPrefixo(String prefixo) {
        return Query.query(Criteria.where("_id").regex(Pattern.compile("^" + Pattern.quote(prefixo))));
    }

    private static UserDocument paraDocumento(UsuarioDeMassa u) {
        ConsentimentoItem aceite = ConsentimentoItem.builder().aceito(true).data(u.criadoEm()).versao("1.0").build();
        return UserDocument.builder()
                .id(u.id())
                .fullName(u.nome())
                .email(u.email())
                .senhaHash(u.senhaHash())
                .papel(Papel.USER)
                .consentimentos(ConsentimentosDocument.builder().termosUso(aceite).build())
                .active(true)
                .emailVerificado(true)
                .createdAt(u.criadoEm())
                .updatedAt(u.criadoEm())
                .build();
    }
}
