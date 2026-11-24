package br.com.saude_monitor.api.hospital.seed;

/** Resultado de {@code SeedRunner#salvarUpsert}, distinto de um simples booleano para poder
 * contabilizar separadamente o caso em que o upsert foi recusado. */
enum ResultadoUpsert { NOVO, ATUALIZADO, IGNORADO_TIPO_DIVERGENTE }
