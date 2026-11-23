package br.com.saude_monitor.api.auth.service;

import java.time.Duration;

/** Espera; nos testes, uma que só registra. */
@FunctionalInterface
interface Espera {
    void esperar(Duration duracao) throws InterruptedException;
}
