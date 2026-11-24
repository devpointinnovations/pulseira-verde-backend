package br.com.saude_monitor.api.visita.controller;

import br.com.saude_monitor.api.visita.dto.ConflitoGeofenceResponse;
import br.com.saude_monitor.api.visita.service.ConflitoGeofenceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

/**
 * Erros próprios da visita. Vem antes do tratador geral para o empate de geofences não cair no 500 genérico.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VisitaExceptionHandler {

    /** Empate de geofences sobrepostos no check-in (E2-04/RN-05) — 409 com candidatos para o app perguntar. */
    @ExceptionHandler(ConflitoGeofenceException.class)
    public ResponseEntity<ConflitoGeofenceResponse> handleConflitoGeofence(ConflitoGeofenceException ex) {
        ConflitoGeofenceResponse body = ConflitoGeofenceResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .code("CONFLITO_GEOFENCE")
                .message(ex.getMessage())
                .candidatos(ex.getCandidatos())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
