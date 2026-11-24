package br.com.saude_monitor.api.hospital.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Totais do log final do seed: o upsert recusado conta como descartado e não como gravado. */
class ContagemDoSeedTest {

    private final ContagemDoSeed contagem = new ContagemDoSeed();

    @Test
    void contaNovoEAtualizadoComoGravados() {
        assertTrue(contagem.registrarResultado(ResultadoUpsert.NOVO));
        assertTrue(contagem.registrarResultado(ResultadoUpsert.ATUALIZADO));
        assertTrue(contagem.registrarResultado(ResultadoUpsert.ATUALIZADO));

        assertEquals(1, contagem.novos);
        assertEquals(2, contagem.atualizados);
        assertEquals(0, contagem.descartados);
    }

    @Test
    void upsertRecusadoPorTipoDivergenteContaComoDescartadoENaoComoGravado() {
        assertFalse(contagem.registrarResultado(ResultadoUpsert.IGNORADO_TIPO_DIVERGENTE));

        assertEquals(1, contagem.descartados);
        assertEquals(0, contagem.novos);
        assertEquals(0, contagem.atualizados);
    }
}
