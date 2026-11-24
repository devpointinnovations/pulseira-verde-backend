package br.com.saude_monitor.api.config.ratelimit;

import lombok.Builder;

/** Chave do limite e como ela aparece no log (sem e-mail nem id em claro). */
@Builder
record IdentidadeDoLimite(String chave, String descricao) {
}
