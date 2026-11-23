package br.com.saude_monitor.api.hospital.seed;

/** Totais da execução, para o log final. */
final class ContagemDoSeed {
    int novos;
    int atualizados;
    int descartados;
    int gemeosIgnorados;
    int camadas;

    /** Soma o resultado da gravação; devolve se o documento foi gravado. */
    boolean registrarResultado(ResultadoUpsert resultado) {
        switch (resultado) {
            case NOVO -> novos++;
            case ATUALIZADO -> atualizados++;
            case IGNORADO_TIPO_DIVERGENTE -> descartados++;
        }
        return resultado != ResultadoUpsert.IGNORADO_TIPO_DIVERGENTE;
    }
}
