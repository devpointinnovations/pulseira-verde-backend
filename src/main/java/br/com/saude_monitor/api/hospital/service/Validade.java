package br.com.saude_monitor.api.hospital.service;

import java.time.Instant;
import lombok.Builder;

@Builder
record Validade<T>(T valor, Instant expiraEm) {
}
