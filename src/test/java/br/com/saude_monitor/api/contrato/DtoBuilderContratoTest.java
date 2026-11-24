package br.com.saude_monitor.api.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato dos DTOs que ganharam {@code @Builder}: criar pelo {@code builder()} dá o mesmo objeto e o mesmo
 * JSON (campos, ordem e nulos) que o construtor, o builder tem um método por componente e o JSON volta a ser o
 * mesmo objeto na leitura.
 */
class DtoBuilderContratoTest {

    private static final String BASE = "br.com.saude_monitor.api.";

    private static final List<String> RECORDS = List.of(
            "auth.dto.AuthResponse", "auth.dto.PainelAuthResponse", "auth.dto.UsuarioDto",
            "config.exception.ApiError", "configuracao.dto.ConfiguracaoRemotaResponse",
            "feedback.dto.FeedbackPendenteResponse", "feedback.dto.FeedbackResponse",
            "hospital.dto.CatalogoResponse", "hospital.dto.EnderecoDto", "hospital.dto.HospitalResponse",
            "hospital.dto.HospitalResumoResponse", "hospital.dto.IndicadoresResponse", "hospital.dto.PageResponse",
            "hospital.dto.SugestaoHospitalDetalheResponse", "hospital.dto.SugestaoHospitalResponse",
            "hospital.migration.EstabelecimentoSaudeRaw", "indicador.dto.IndicadoresDetalheResponse",
            "user.dto.ConsentimentosResponse", "user.dto.UserResponse",
            "visita.dto.CandidatoGeofence", "visita.dto.CheckinRequest", "visita.dto.CheckinResponse",
            "visita.dto.CheckoutRequest", "visita.dto.CheckoutResponse", "visita.dto.ConflitoGeofenceResponse",
            "visita.dto.VisitaResponse");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Instant INSTANTE = Instant.parse("2026-11-24T12:00:00Z");

    @TestFactory
    Stream<DynamicTest> builderEConstrutorDaoOMesmoObjetoEOMesmoJson() {
        return RECORDS.stream().map(nome -> DynamicTest.dynamicTest(nome, () -> {
            Class<?> tipo = Class.forName(BASE + nome);
            Object porConstrutor = criarPorConstrutor(tipo);
            Object porBuilder = criarPorBuilder(tipo, porConstrutor);

            assertThat(porBuilder).isEqualTo(porConstrutor);
            String json = MAPPER.writeValueAsString(porConstrutor);
            assertThat(MAPPER.writeValueAsString(porBuilder)).isEqualTo(json);

            List<String> componentes = new ArrayList<>();
            for (RecordComponent c : tipo.getRecordComponents()) {
                componentes.add(c.getName());
            }
            JsonNode no = MAPPER.readTree(json);
            List<String> campos = new ArrayList<>();
            no.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).isNotEmpty();
            assertThat(componentes).containsSubsequence(campos);
        }));
    }

    @TestFactory
    Stream<DynamicTest> jsonVoltaAoMesmoObjetoNaLeitura() {
        return RECORDS.stream().map(nome -> DynamicTest.dynamicTest(nome, () -> {
            Class<?> tipo = Class.forName(BASE + nome);
            Object original = criarPorConstrutor(tipo);

            Object lido = MAPPER.readValue(MAPPER.writeValueAsString(original), tipo);

            assertThat(lido).isEqualTo(original);
        }));
    }

    private static Object criarPorConstrutor(Class<?> tipo) throws Exception {
        RecordComponent[] componentes = tipo.getRecordComponents();
        Class<?>[] tipos = new Class<?>[componentes.length];
        Object[] valores = new Object[componentes.length];
        for (int i = 0; i < componentes.length; i++) {
            tipos[i] = componentes[i].getType();
            valores[i] = amostra(componentes[i].getType());
        }
        Constructor<?> construtor = tipo.getDeclaredConstructor(tipos);
        construtor.setAccessible(true);
        return construtor.newInstance(valores);
    }

    private static Object criarPorBuilder(Class<?> tipo, Object modelo) throws Exception {
        Object builder = tipo.getMethod("builder").invoke(null);
        for (RecordComponent c : tipo.getRecordComponents()) {
            Method acessor = c.getAccessor();
            acessor.setAccessible(true);
            Method setter = builder.getClass().getMethod(c.getName(), c.getType());
            setter.invoke(builder, acessor.invoke(modelo));
        }
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private static Object amostra(Class<?> tipo) throws Exception {
        if (tipo == String.class || tipo == Object.class || tipo == CharSequence.class) {
            return "x";
        }
        if (tipo == int.class || tipo == Integer.class) {
            return 1;
        }
        if (tipo == long.class || tipo == Long.class) {
            return 2L;
        }
        if (tipo == double.class || tipo == Double.class) {
            return 1.5d;
        }
        if (tipo == boolean.class || tipo == Boolean.class) {
            return true;
        }
        if (tipo == Instant.class) {
            return INSTANTE;
        }
        if (tipo == List.class) {
            return List.of();
        }
        if (tipo == Set.class) {
            return Set.of();
        }
        if (tipo == Map.class) {
            return Map.of();
        }
        if (tipo.isEnum()) {
            return tipo.getEnumConstants()[0];
        }
        if (tipo.isArray()) {
            return Array.newInstance(tipo.getComponentType(), 0);
        }
        if (tipo.isRecord()) {
            return criarPorConstrutor(tipo);
        }
        return null;
    }
}
