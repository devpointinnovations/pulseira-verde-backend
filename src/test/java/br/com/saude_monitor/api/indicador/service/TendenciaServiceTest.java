package br.com.saude_monitor.api.indicador.service;

import br.com.saude_monitor.api.feedback.dto.FeedbackParaIndicador;
import br.com.saude_monitor.api.indicador.dto.TendenciaNota;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da tendência simples da nota (E4-06): limiar de estabilidade, selo preliminar, corte de
 * 5 avaliações por período e separação dos períodos pela data de criação.
 */
class TendenciaServiceTest {

    private static final Instant FIM = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant NO_ATUAL = FIM.minus(Duration.ofDays(10));
    private static final Instant NO_ANTERIOR = FIM.minus(Duration.ofDays(40));

    private static FeedbackParaIndicador feedback(int nota, Instant criadoEm) {
        return FeedbackParaIndicador.builder().visitaId("v").nota(nota).criadoEm(criadoEm).build();
    }

    private static List<FeedbackParaIndicador> repetir(int quantidade, int nota, Instant criadoEm) {
        List<FeedbackParaIndicador> lista = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            lista.add(feedback(nota, criadoEm));
        }
        return lista;
    }

    private static List<FeedbackParaIndicador> juntar(List<FeedbackParaIndicador> a, List<FeedbackParaIndicador> b) {
        List<FeedbackParaIndicador> lista = new ArrayList<>(a);
        lista.addAll(b);
        return lista;
    }

    // ------------------------------------------------ sentido e variação ------------------------------------------

    @Test
    void notaQueSobeMaisQueOLimiarFicaSubindo() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(10, 5, NO_ATUAL), repetir(10, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(resultado.variacao()).isEqualTo(2.0);
        assertThat(resultado.preliminar()).isFalse();
    }

    @Test
    void notaQueCaiMaisQueOLimiarFicaCaindo() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(10, 2, NO_ATUAL), repetir(10, 4, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.CAINDO);
        assertThat(resultado.variacao()).isEqualTo(-2.0);
    }

    @Test
    void variacaoAbaixoDoLimiarFicaEstavel() {
        // Atual: nove 4 e um 5 = 4,1; anterior: dez 4 = 4,0. Variação 0,1.
        List<FeedbackParaIndicador> atual = juntar(repetir(9, 4, NO_ATUAL), repetir(1, 5, NO_ATUAL));
        var resultado = TendenciaService.calcular(juntar(atual, repetir(10, 4, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.ESTAVEL);
        assertThat(resultado.variacao()).isEqualTo(0.1);
    }

    @Test
    void variacaoExatamenteNoLimiarJaContaComoMudanca() {
        // Atual: sete 4 e três 5 = 4,3; anterior: dez 4 = 4,0. Em double, 4,3 − 4,0 = 0,2999…;
        // o arredondamento a duas casas evita que a variação de 0,3 vire "estável".
        List<FeedbackParaIndicador> atual = juntar(repetir(7, 4, NO_ATUAL), repetir(3, 5, NO_ATUAL));
        var resultado = TendenciaService.calcular(juntar(atual, repetir(10, 4, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(resultado.variacao()).isEqualTo(0.3);
    }

    @ParameterizedTest
    @CsvSource({
            "0.29, ESTAVEL",
            "-0.29, ESTAVEL",
            "0.00, ESTAVEL",
            "0.30, SUBINDO",
            "-0.30, CAINDO",
            "1.50, SUBINDO",
            "-4.00, CAINDO"
    })
    void sentidoAplicaOLimiarDeEstabilidade(String variacao, TendenciaNota esperado) {
        assertThat(TendenciaService.sentido(new BigDecimal(variacao))).isEqualTo(esperado);
    }

    @Test
    void variacaoArredondaEmDuasCasas() {
        // Atual: 4,4,5,5,5,5 = 28/6 = 4,666…; anterior: cinco 4 = 4,0. Variação 0,67.
        List<FeedbackParaIndicador> atual = juntar(repetir(2, 4, NO_ATUAL), repetir(4, 5, NO_ATUAL));
        var resultado = TendenciaService.calcular(juntar(atual, repetir(5, 4, NO_ANTERIOR)), FIM);

        assertThat(resultado.variacao()).isEqualTo(0.67);
    }

    // ------------------------------------------------ preliminar (N < 10) ------------------------------------------

    @Test
    void periodoAtualComMenosDe10AvaliacoesEhPreliminar() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(9, 5, NO_ATUAL), repetir(20, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(resultado.preliminar()).isTrue();
    }

    @Test
    void periodoAnteriorComMenosDe10AvaliacoesEhPreliminar() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(20, 5, NO_ATUAL), repetir(5, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado.preliminar()).isTrue();
    }

    @Test
    void dezAvaliacoesEmCadaPeriodoNaoEhPreliminar() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(10, 5, NO_ATUAL), repetir(10, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado.preliminar()).isFalse();
    }

    // ------------------------------------------------ sem dado -----------------------------------------------------

    @Test
    void semCincoAvaliacoesNoPeriodoAtualNaoHaTendencia() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(4, 5, NO_ATUAL), repetir(20, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado).isEqualTo(TendenciaService.Resultado.SEM_DADOS);
    }

    @Test
    void semCincoAvaliacoesNoPeriodoAnteriorNaoHaTendencia() {
        var resultado = TendenciaService.calcular(
                juntar(repetir(20, 5, NO_ATUAL), repetir(4, 3, NO_ANTERIOR)), FIM);

        assertThat(resultado.tendencia()).isNull();
        assertThat(resultado.variacao()).isNull();
        assertThat(resultado.preliminar()).isNull();
    }

    @Test
    void listaVaziaNulaOuSemFimNaoTemTendencia() {
        assertThat(TendenciaService.calcular(List.of(), FIM)).isEqualTo(TendenciaService.Resultado.SEM_DADOS);
        assertThat(TendenciaService.calcular(null, FIM)).isEqualTo(TendenciaService.Resultado.SEM_DADOS);
        assertThat(TendenciaService.calcular(repetir(10, 5, NO_ATUAL), null))
                .isEqualTo(TendenciaService.Resultado.SEM_DADOS);
    }

    @Test
    void feedbacksSemDataOuComNotaInvalidaNaoContam() {
        // Cinco válidos em cada período; os inválidos somariam o sexto no atual e mudariam a média.
        List<FeedbackParaIndicador> lista = juntar(repetir(5, 4, NO_ATUAL), repetir(5, 4, NO_ANTERIOR));
        lista.add(feedback(1, null));
        lista.add(feedback(0, NO_ATUAL));
        lista.add(feedback(6, NO_ATUAL));
        lista.add(FeedbackParaIndicador.builder().visitaId("v").nota(null).criadoEm(NO_ATUAL).build());

        var resultado = TendenciaService.calcular(lista, FIM);

        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.ESTAVEL);
        assertThat(resultado.variacao()).isEqualTo(0.0);
    }

    // ------------------------------------------------ limites dos períodos -----------------------------------------

    @Test
    void feedbackNoInstanteDoCorteContaNoPeriodoAnterior() {
        Instant corte = FIM.minus(TendenciaService.JANELA);
        // Quatro 5 no atual + um 5 exatamente no corte: o do corte vai para o anterior,
        // então o atual fica com 4 e não há tendência.
        List<FeedbackParaIndicador> lista = juntar(repetir(4, 5, NO_ATUAL), repetir(4, 3, NO_ANTERIOR));
        lista.add(feedback(5, corte));

        assertThat(TendenciaService.calcular(lista, FIM)).isEqualTo(TendenciaService.Resultado.SEM_DADOS);

        lista.add(feedback(5, FIM));
        var resultado = TendenciaService.calcular(lista, FIM);
        // Atual: cinco 5 = 5,0; anterior: quatro 3 e um 5 = 3,4.
        assertThat(resultado.tendencia()).isEqualTo(TendenciaNota.SUBINDO);
        assertThat(resultado.variacao()).isEqualTo(1.6);
    }

    @Test
    void feedbackAnteriorAosDoisPeriodosOuDepoisDoFimNaoConta() {
        Instant inicioAnterior = FIM.minus(TendenciaService.JANELA.multipliedBy(2));
        List<FeedbackParaIndicador> lista = juntar(repetir(5, 4, NO_ATUAL), repetir(4, 4, NO_ANTERIOR));
        lista.add(feedback(1, inicioAnterior));
        lista.add(feedback(1, FIM.minus(Duration.ofDays(80))));
        lista.add(feedback(1, FIM.plusSeconds(1)));

        assertThat(TendenciaService.calcular(lista, FIM)).isEqualTo(TendenciaService.Resultado.SEM_DADOS);
    }
}
