package br.com.saude_monitor.api.hospital.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Um valor montado com o instante em que vence. */
final class Guardado<T> {

    private final AtomicReference<Validade<T>> atual = new AtomicReference<>();

    T obterOuMontar(Clock clock, Supplier<T> montar) {
        Validade<T> lido = atual.get();
        if (lido != null && clock.instant().isBefore(lido.expiraEm())) {
            return lido.valor();
        }
        return remontar(clock, montar);
    }

    private synchronized T remontar(Clock clock, Supplier<T> montar) {
        Instant agora = clock.instant();
        Validade<T> lido = atual.get();
        if (lido != null && agora.isBefore(lido.expiraEm())) {
            return lido.valor(); // outra thread remontou enquanto esta esperava
        }
        T valor = montar.get();
        atual.set(Validade.<T>builder().valor(valor).expiraEm(agora.plus(CatalogoService.VALIDADE)).build());
        return valor;
    }

    void descartar() {
        atual.set(null);
    }
}
