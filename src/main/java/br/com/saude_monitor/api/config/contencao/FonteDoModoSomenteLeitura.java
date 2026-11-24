package br.com.saude_monitor.api.config.contencao;

/**
 * De onde o filtro de contenção lê o modo somente leitura. O módulo {@code configuracao} implementa; assim a
 * contenção não depende de nenhum módulo de negócio.
 */
public interface FonteDoModoSomenteLeitura {

    SomenteLeitura somenteLeitura();
}
