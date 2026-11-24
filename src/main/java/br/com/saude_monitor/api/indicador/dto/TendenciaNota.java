package br.com.saude_monitor.api.indicador.dto;

/**
 * Sentido da nota média de um hospital no período atual em relação ao anterior (E4-06).
 *
 * <p>{@link #ESTAVEL} quando a variação, em módulo, fica abaixo do limiar de estabilidade.</p>
 */
public enum TendenciaNota {
    SUBINDO,
    ESTAVEL,
    CAINDO
}
