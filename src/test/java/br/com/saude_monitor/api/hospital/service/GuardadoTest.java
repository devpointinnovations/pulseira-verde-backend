package br.com.saude_monitor.api.hospital.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O valor guardado vale por {@link CatalogoService#VALIDADE}; depois disso é montado de novo. */
class GuardadoTest {

    private static final Instant INICIO = Instant.parse("2026-11-23T12:00:00Z");

    private final AtomicInteger montagens = new AtomicInteger();
    private final Guardado<String> guardado = new Guardado<>();

    private String montar() {
        return "valor-" + montagens.incrementAndGet();
    }

    private Clock relogioEm(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    @Test
    void reaproveitaOValorAteVencer() {
        assertEquals("valor-1", guardado.obterOuMontar(relogioEm(INICIO), this::montar));
        assertEquals("valor-1", guardado.obterOuMontar(
                relogioEm(INICIO.plus(CatalogoService.VALIDADE).minusSeconds(1)), this::montar));
        assertEquals(1, montagens.get());
    }

    @Test
    void montaDeNovoQuandoOValorVence() {
        guardado.obterOuMontar(relogioEm(INICIO), this::montar);

        assertEquals("valor-2", guardado.obterOuMontar(relogioEm(INICIO.plus(CatalogoService.VALIDADE)), this::montar));
    }

    @Test
    void descartarObrigaAMontarDeNovoMesmoDentroDaValidade() {
        guardado.obterOuMontar(relogioEm(INICIO), this::montar);

        guardado.descartar();

        assertEquals("valor-2", guardado.obterOuMontar(relogioEm(INICIO.plus(Duration.ofSeconds(1))), this::montar));
    }
}
