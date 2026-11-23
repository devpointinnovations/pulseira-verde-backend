package br.com.saude_monitor.api.visita.seed;

import br.com.saude_monitor.api.hospital.document.CategoriaEstabelecimento;
import br.com.saude_monitor.api.hospital.document.HospitalDocument;
import br.com.saude_monitor.api.hospital.document.TipoEstabelecimento;
import br.com.saude_monitor.api.hospital.repository.HospitalRepository;
import br.com.saude_monitor.api.visita.document.OrigemVisita;
import br.com.saude_monitor.api.visita.document.StatusVisita;
import br.com.saude_monitor.api.visita.document.VisitaDocument;
import br.com.saude_monitor.api.visita.dto.CheckinRequest;
import br.com.saude_monitor.api.visita.dto.CheckinResponse;
import br.com.saude_monitor.api.visita.service.VisitaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2 da auditoria de arquitetura (23/11/2026): geofence, fila offline e toque manual podem
 * pedir check-in ao mesmo tempo. Contra um MongoDB real, mede que nasce uma só visita ativa
 * por pessoa e que os perdedores recebem a visita que venceu.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "app.geofence.reconciliacao.enabled=false")
class VisitaAtivaUnicaIntegracaoTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
    }

    private static final int PEDIDOS = 12;

    @Autowired
    private VisitaService visitaService;
    @Autowired
    private VisitaAtivaUnicaRunner runner;
    @Autowired
    private HospitalRepository hospitalRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    private String hospitalId;

    @BeforeEach
    void preparar() {
        mongoTemplate.remove(new Query(), VisitaDocument.class);
        hospitalId = hospitalRepository.save(HospitalDocument.builder()
                .nome("Hospital da Corrida")
                .tipo(TipoEstabelecimento.PUBLICO)
                .categoria(CategoriaEstabelecimento.HOSPITAL)
                .localizacao(new GeoJsonPoint(-48.07, -15.90))
                .ativo(true)
                .build()).getId();
    }

    @Test
    void checkinsSimultaneosDoMesmoUsuarioCriamUmaSoVisita() throws Exception {
        List<CheckinResponse> respostas = emParalelo(() -> visitaService.checkin(
                new CheckinRequest(hospitalId, OrigemVisita.MANUAL, null, "aparelho-1", null), "usuario-1"));

        assertThat(ativas("usuarioId", "usuario-1")).hasSize(1);
        assertThat(respostas).filteredOn(CheckinResponse::criado).hasSize(1);
        assertThat(respostas).extracting(CheckinResponse::id).containsOnly(respostas.getFirst().id());
    }

    @Test
    void checkinsSimultaneosDoMesmoAparelhoAnonimoCriamUmaSoVisita() throws Exception {
        List<CheckinResponse> respostas = emParalelo(() -> visitaService.checkin(
                new CheckinRequest(hospitalId, OrigemVisita.MANUAL, null, "aparelho-anon", null), null));

        assertThat(ativas("dispositivoId", "aparelho-anon")).hasSize(1);
        assertThat(respostas).filteredOn(CheckinResponse::criado).hasSize(1);
    }

    @Test
    void mesmoAparelhoPodeTerVisitaAnonimaEVisitaLogada() {
        visitaService.checkin(new CheckinRequest(hospitalId, OrigemVisita.MANUAL, null, "aparelho-2", null), null);
        CheckinResponse logada = visitaService.checkin(
                new CheckinRequest(hospitalId, OrigemVisita.MANUAL, null, "aparelho-2", null), "usuario-2");

        assertThat(logada.criado()).isTrue();
        assertThat(ativas("dispositivoId", "aparelho-2")).hasSize(2);
    }

    @Test
    void bancoRecusaSegundaVisitaAtivaMasAceitaDepoisDeEncerrada() {
        mongoTemplate.save(visita("usuario-3", StatusVisita.FINALIZADA, Instant.now().minusSeconds(7200)));
        mongoTemplate.save(visita("usuario-3", StatusVisita.SUSPEITA, Instant.now().minusSeconds(3600)));

        assertThatThrownBy(() -> mongoTemplate.save(visita("usuario-3", StatusVisita.EM_ATENDIMENTO, Instant.now())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void runnerExpiraDuplicadasAntigasECriaOsIndices() {
        mongoTemplate.indexOps(VisitaDocument.class).dropIndex(VisitaAtivaUnicaRunner.INDICE_USUARIO);
        mongoTemplate.indexOps(VisitaDocument.class).dropIndex(VisitaAtivaUnicaRunner.INDICE_DISPOSITIVO);
        Instant agora = Instant.now();
        VisitaDocument antiga = mongoTemplate.save(visita("usuario-4", StatusVisita.EM_ATENDIMENTO, agora.minusSeconds(600)));
        VisitaDocument recente = mongoTemplate.save(visita("usuario-4", StatusVisita.SUSPEITA, agora));

        runner.run(null);

        assertThat(mongoTemplate.findById(antiga.getId(), VisitaDocument.class).getStatus()).isEqualTo(StatusVisita.EXPIRADA);
        assertThat(mongoTemplate.findById(recente.getId(), VisitaDocument.class).getStatus()).isEqualTo(StatusVisita.SUSPEITA);
        assertThat(mongoTemplate.indexOps(VisitaDocument.class).getIndexInfo()).extracting(IndexInfo::getName)
                .contains(VisitaAtivaUnicaRunner.INDICE_USUARIO, VisitaAtivaUnicaRunner.INDICE_DISPOSITIVO);
    }

    private List<CheckinResponse> emParalelo(Callable<CheckinResponse> checkin) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(PEDIDOS)) {
            var futuros = IntStream.range(0, PEDIDOS).mapToObj(i -> executor.submit(() -> {
                largada.await();
                return checkin.call();
            })).toList();
            largada.countDown();
            List<CheckinResponse> respostas = new java.util.ArrayList<>();
            for (var f : futuros) {
                respostas.add(f.get());
            }
            return respostas;
        }
    }

    private List<VisitaDocument> ativas(String campo, String valor) {
        return mongoTemplate.find(new Query(Criteria.where(campo).is(valor).and("status").in(StatusVisita.ATIVOS)),
                VisitaDocument.class);
    }

    private VisitaDocument visita(String usuarioId, StatusVisita status, Instant entrada) {
        return VisitaDocument.builder()
                .usuarioId(usuarioId)
                .hospitalId(hospitalId)
                .entrada(entrada)
                .ultimoHeartbeat(entrada)
                .status(status)
                .build();
    }
}
