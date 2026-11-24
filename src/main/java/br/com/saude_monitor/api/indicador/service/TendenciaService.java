package br.com.saude_monitor.api.indicador.service;

import br.com.saude_monitor.api.feedback.dto.FeedbackParaIndicador;
import br.com.saude_monitor.api.indicador.dto.TendenciaNota;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tendência simples da nota de um hospital (E4-06): compara a nota média do período atual com a
 * do período anterior, de mesmo tamanho e colado nele.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>período atual = {@code (fim - JANELA, fim]}; período anterior =
 *       {@code (fim - 2×JANELA, fim - JANELA]}, pela data de criação do feedback;</li>
 *   <li>a nota de cada período segue as mesmas regras da nota pública (média das notas 1–5 dos
 *       feedbacks já filtrados pela prova de presença);</li>
 *   <li>sem {@value #MINIMO_POR_PERIODO} avaliações em cada período, não há tendência (mesmo
 *       corte da RN-15);</li>
 *   <li>variação = nota atual − nota anterior, arredondada a duas casas; em módulo abaixo de
 *       {@link #LIMIAR_ESTABILIDADE} é {@link TendenciaNota#ESTAVEL};</li>
 *   <li>preliminar quando algum dos dois períodos tem menos de
 *       {@value #MINIMO_CONSOLIDADO} avaliações (critério de aceite do E4-06).</li>
 * </ul>
 *
 * <p>Funções puras, sem banco, para teste unitário direto.</p>
 */
public final class TendenciaService {

    /** Tamanho de cada período comparado. */
    public static final Duration JANELA = Duration.ofDays(30);

    /** Avaliações mínimas em cada período para haver tendência (mesmo corte da RN-15). */
    public static final int MINIMO_POR_PERIODO = 5;

    /** Abaixo deste número de avaliações em algum período, a tendência é preliminar. */
    public static final int MINIMO_CONSOLIDADO = 10;

    /** Variação, em módulo, abaixo da qual a nota é considerada estável. */
    public static final BigDecimal LIMIAR_ESTABILIDADE = new BigDecimal("0.3");

    /** Tendência calculada; {@link #SEM_DADOS} quando não há avaliações suficientes. */
    public record Resultado(TendenciaNota tendencia, Double variacao, Boolean preliminar) {

        /** Sem tendência: todos os campos nulos. */
        public static final Resultado SEM_DADOS = new Resultado(null, null, null);
    }

    private TendenciaService() {
    }

    /**
     * Calcula a tendência dos feedbacks informados em relação ao instante {@code fim}.
     * Feedbacks sem data de criação, sem nota válida ou fora dos dois períodos são ignorados.
     */
    public static Resultado calcular(List<FeedbackParaIndicador> feedbacks, Instant fim) {
        if (feedbacks == null || feedbacks.isEmpty() || fim == null) {
            return Resultado.SEM_DADOS;
        }
        Instant corte = fim.minus(JANELA);
        Instant inicioAnterior = corte.minus(JANELA);

        List<Integer> atual = new ArrayList<>();
        List<Integer> anterior = new ArrayList<>();
        for (FeedbackParaIndicador f : feedbacks) {
            Instant criadoEm = f.criadoEm();
            if (criadoEm == null || !notaValida(f.nota()) || criadoEm.isAfter(fim)) {
                continue;
            }
            if (criadoEm.isAfter(corte)) {
                atual.add(f.nota());
            } else if (criadoEm.isAfter(inicioAnterior)) {
                anterior.add(f.nota());
            }
        }

        if (atual.size() < MINIMO_POR_PERIODO || anterior.size() < MINIMO_POR_PERIODO) {
            return Resultado.SEM_DADOS;
        }

        BigDecimal variacao = BigDecimal.valueOf(EstatisticaService.notaMedia(atual))
                .subtract(BigDecimal.valueOf(EstatisticaService.notaMedia(anterior)))
                .setScale(2, RoundingMode.HALF_UP);
        boolean preliminar = Math.min(atual.size(), anterior.size()) < MINIMO_CONSOLIDADO;
        return new Resultado(sentido(variacao), variacao.doubleValue(), preliminar);
    }

    /** Sentido da variação, com o limiar de estabilidade aplicado sobre o valor arredondado. */
    static TendenciaNota sentido(BigDecimal variacao) {
        if (variacao.abs().compareTo(LIMIAR_ESTABILIDADE) < 0) {
            return TendenciaNota.ESTAVEL;
        }
        return variacao.signum() > 0 ? TendenciaNota.SUBINDO : TendenciaNota.CAINDO;
    }

    private static boolean notaValida(Integer nota) {
        return nota != null && nota >= 1 && nota <= 5;
    }
}
