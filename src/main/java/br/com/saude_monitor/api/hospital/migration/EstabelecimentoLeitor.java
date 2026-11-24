package br.com.saude_monitor.api.hospital.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê a fonte de importação de estabelecimentos (CSV ou JSON) e converte cada linha
 * em {@link EstabelecimentoSaudeRaw}.
 *
 * <p>As chaves das colunas são normalizadas (minúsculas, sem acentos, sem separadores)
 * para tolerar variações comuns de cabeçalho entre fontes CNES/DATASUS — por exemplo,
 * {@code CODIGO_CNES}, {@code codigoCnes} e {@code código cnes} são equivalentes.</p>
 */
@Component
public class EstabelecimentoLeitor {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final CsvMapper csvMapper = new CsvMapper();

    /**
     * Lê o arquivo no formato indicado ({@code CSV} ou {@code JSON}) e retorna os registros
     * já normalizados. Lança {@link IllegalArgumentException} para formato desconhecido.
     */
    public List<EstabelecimentoSaudeRaw> ler(Path arquivo, String formato) {
        try {
            List<Map<String, String>> linhas = "JSON".equalsIgnoreCase(formato)
                    ? lerJson(arquivo)
                    : lerCsv(arquivo);

            List<EstabelecimentoSaudeRaw> registros = new ArrayList<>(linhas.size());
            for (Map<String, String> linha : linhas) {
                Map<String, String> canonicas = normalizarChaves(linha);
                registros.add(paraRegistro(canonicas));
            }
            return registros;
        } catch (IOException e) {
            throw new IllegalArgumentException("Falha ao ler arquivo de importação: " + arquivo, e);
        }
    }

    private List<Map<String, String>> lerCsv(Path arquivo) throws IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (var in = Files.newInputStream(arquivo)) {
            MappingIterator<Map<String, String>> iterator = csvMapper
                    .readerFor(Map.class)
                    .with(schema)
                    .readValues(in);
            return iterator.readAll();
        }
    }

    private List<Map<String, String>> lerJson(Path arquivo) throws IOException {
        JsonNode raiz;
        try (var in = Files.newInputStream(arquivo)) {
            raiz = jsonMapper.readTree(in);
        }
        List<Map<String, String>> linhas = new ArrayList<>();
        if (raiz.isArray()) {
            for (JsonNode elemento : raiz) {
                Map<String, String> linha = new HashMap<>();
                elemento.fields().forEachRemaining(e -> linha.put(e.getKey(), e.getValue().asText(null)));
                linhas.add(linha);
            }
        }
        return linhas;
    }

    /** Normaliza as chaves de uma linha para acesso tolerante (minúsculas, sem acentos, sem separadores). */
    private Map<String, String> normalizarChaves(Map<String, String> linha) {
        Map<String, String> canonicas = new HashMap<>();
        linha.forEach((chave, valor) -> canonicas.put(canonicalizar(chave), valor));
        return canonicas;
    }

    private String canonicalizar(String chave) {
        String semAcento = Normalizer.normalize(chave, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private EstabelecimentoSaudeRaw paraRegistro(Map<String, String> c) {
        return EstabelecimentoSaudeRaw.builder()
                .codigoCnes(pegar(c, "codigocnes"))
                .cnpj(pegar(c, "cnpj"))
                .razaoSocial(pegar(c, "razaosocial"))
                .nomeFantasia(pegar(c, "nomefantasia"))
                .logradouro(pegar(c, "logradouro"))
                .numero(pegar(c, "numero"))
                .complemento(pegar(c, "complemento"))
                .bairro(pegar(c, "bairro"))
                .municipio(pegar(c, "municipio"))
                .uf(pegar(c, "uf"))
                .cep(pegar(c, "cep"))
                .telefone(pegar(c, "telefone"))
                .email(pegar(c, "email"))
                .tipoUnidade(pegar(c, "tipounidade"))
                .descricaoTipoUnidade(pegar(c, "descricaotipounidade"))
                .latitude(parseDouble(pegar(c, "latitude", "lat")))
                .longitude(parseDouble(pegar(c, "longitude", "lon", "lng")))
                .build();
    }

    private String pegar(Map<String, String> c, String... chaves) {
        for (String chave : chaves) {
            String valor = c.get(chave);
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private Double parseDouble(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            // Aceita vírgula decimal, comum em CSVs brasileiros.
            return Double.parseDouble(valor.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
