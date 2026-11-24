package br.com.saude_monitor.api.config.contencao;

import lombok.Builder;

/** Estado do modo somente leitura: se está ligado e a mensagem a mostrar (vazia usa a mensagem padrão). */
@Builder
public record SomenteLeitura(boolean ativo, String mensagem) {
}
