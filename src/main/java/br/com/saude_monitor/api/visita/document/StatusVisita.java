package br.com.saude_monitor.api.visita.document;

import java.util.List;

/**
 * Status do ciclo de vida de uma visita (Épico 02 — Detecção de Visitas).
 *
 * <p>Transições: {@code EM_ATENDIMENTO} → {@code SUSPEITA} (2h sem heartbeat, RN-23) →
 * volta a {@code EM_ATENDIMENTO} (heartbeat) ou {@code EXPIRADA} (24h sem heartbeat, RN-04).
 * {@code EM_ATENDIMENTO}/{@code SUSPEITA} → {@code FINALIZADA} (checkout normal, RN-03) ou
 * {@code GPS_INTERROMPIDO} (visita sem sinal de GPS por 45min, RN-06/E2-05).</p>
 */
public enum StatusVisita {
    EM_ATENDIMENTO,
    SUSPEITA,
    FINALIZADA,
    EXPIRADA,
    GPS_INTERROMPIDO,

    /** Visita encerrada cuja janela de resposta de feedback (24h) expirou sem resposta (RN-09). */
    SEM_FEEDBACK;

    /** Status de visita em curso: uma pessoa só pode ter uma (índice único parcial em {@code visitas}). */
    public static final List<StatusVisita> ATIVOS = List.of(EM_ATENDIMENTO, SUSPEITA);
}
