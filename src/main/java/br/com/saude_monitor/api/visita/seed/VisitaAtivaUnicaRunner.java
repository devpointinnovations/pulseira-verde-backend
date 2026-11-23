package br.com.saude_monitor.api.visita.seed;

import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Garante, no startup, no máximo uma visita ativa ({@link StatusVisita#ATIVOS}) por pessoa:
 * por {@code usuarioId} nas visitas logadas e por {@code dispositivoId} nas anônimas (R2 da
 * auditoria de arquitetura de 23/11/2026).
 *
 * <p><b>Por que não é {@code @CompoundIndex}:</b> o auto-index do Spring Data cria os índices
 * antes de qualquer runner. Se o banco já tiver duas visitas ativas da mesma pessoa, a
 * criação do índice único falharia no boot e derrubaria o deploy. Aqui as duplicadas antigas
 * são encerradas primeiro, como o job de expiração faria (EXPIRADA, que não entra no
 * indicador), e só então o índice é criado.</p>
 *
 * <p><b>Idempotente:</b> sem duplicadas nada é gravado, e criar um índice que já existe com a
 * mesma definição não faz nada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitaAtivaUnicaRunner implements ApplicationRunner {

    public static final String INDICE_USUARIO = "uk_visita_ativa_usuario";
    public static final String INDICE_DISPOSITIVO = "uk_visita_ativa_dispositivo";

    private static final int TENTATIVAS = 3;

    private static final List<String> ATIVOS = StatusVisita.ATIVOS.stream().map(Enum::name).toList();

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Um check-in duplicado entre a limpeza e a criação do índice o faz falhar: repete a
        // limpeza e a criação. Nenhuma falha aqui derruba o boot; sem índice, o check-in
        // continua como antes (verificar e gravar) até a próxima subida.
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                long encerradas = encerrarDuplicadas(VisitaDocument::getUsuarioId)
                        + encerrarDuplicadas(v -> v.getUsuarioId() == null ? v.getDispositivoId() : null);
                if (encerradas > 0) {
                    log.warn("[VisitaAtivaUnica] {} visita(s) ativa(s) duplicada(s) encerrada(s) como EXPIRADA.", encerradas);
                }
                criarIndices();
                return;
            } catch (DataAccessException e) {
                log.error("[VisitaAtivaUnica] Tentativa {}/{} sem criar o índice único de visita ativa. "
                        + "Se persistir, confira se o índice já existe com outra definição.", tentativa, TENTATIVAS, e);
            }
        }
    }

    void criarIndices() {
        var indices = mongoTemplate.indexOps(VisitaDocument.class);
        indices.createIndex(new Index().on("usuarioId", Sort.Direction.ASC).unique().named(INDICE_USUARIO)
                .partial(PartialIndexFilter.of(Criteria.where("usuarioId").type(2) // 2 = string
                        .and("status").in(ATIVOS))));
        // Só a visita anônima: a logada guarda o aparelho também, e o mesmo aparelho pode
        // trocar de conta. A anonimizada (exclusão de conta, DAD-01) perde o aparelho e sai.
        indices.createIndex(new Index().on("dispositivoId", Sort.Direction.ASC).unique().named(INDICE_DISPOSITIVO)
                .partial(PartialIndexFilter.of(Criteria.where("usuarioId").is(null)
                        .and("dispositivoId").type(2)
                        .and("status").in(ATIVOS))));
    }

    /** Mantém a visita ativa com o sinal de vida mais recente de cada dono e expira as outras. */
    private long encerrarDuplicadas(Function<VisitaDocument, String> dono) {
        // shortcut: lê todas as visitas ativas para a JVM; são só as abertas agora (dezenas a
        // milhares), e após a primeira subida com o índice não sobra duplicada para achar.
        List<VisitaDocument> ativas = mongoTemplate.find(
                new Query(Criteria.where("status").in(ATIVOS)).with(Sort.by(Sort.Direction.DESC, "ultimoHeartbeat", "entrada")),
                VisitaDocument.class);
        Set<String> vistos = new HashSet<>();
        long encerradas = 0;
        for (VisitaDocument v : ativas) {
            String chave = dono.apply(v);
            if (chave != null && !vistos.add(chave)) {
                encerradas += expirar(v);
            }
        }
        return encerradas;
    }

    private long expirar(VisitaDocument v) {
        Instant saida = Objects.requireNonNullElse(v.getUltimoHeartbeat(), v.getEntrada());
        Update update = new Update()
                .set("status", StatusVisita.EXPIRADA)
                .set("saida", saida)
                .set("duracaoMinutos", (int) Math.max(0, Duration.between(v.getEntrada(), saida).toMinutes()))
                .unset("pontosAmostrais");
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(v.getId()).and("status").in(ATIVOS)),
                update, VisitaDocument.class).getModifiedCount();
    }
}
