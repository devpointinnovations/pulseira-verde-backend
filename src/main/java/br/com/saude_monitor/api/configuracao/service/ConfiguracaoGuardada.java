package br.com.saude_monitor.api.configuracao.service;

import br.com.saude_monitor.api.configuracao.dto.ConfiguracaoRemotaResponse;
import java.time.Instant;
import lombok.Builder;

@Builder
record ConfiguracaoGuardada(ConfiguracaoRemotaResponse configuracao, Instant expiraEm) {
}
