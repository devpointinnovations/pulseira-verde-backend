package br.com.saude_monitor.api.indicador.controller;

import br.com.saude_monitor.api.indicador.dto.IndicadoresDetalheResponse;
import br.com.saude_monitor.api.indicador.service.IndicadorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicadores de um hospital. O caminho continua sob {@code /api/v1/hospitais} e o grupo do OpenAPI continua
 * {@code hospital-controller}, para o contrato publicado não mudar.
 */
@RestController
@RequestMapping("/api/v1/hospitais")
@RequiredArgsConstructor
@Tag(name = "hospital-controller")
public class IndicadoresController {

    private final IndicadorService indicadorService;

    /**
     * 🔓 Indicadores públicos enriquecidos do hospital (§3.5 / E4-01..E4-04).
     * 404 se o hospital não existir; 200 com {@code indicadoresDisponiveis=false}
     * quando ainda não há amostra suficiente (RN-15).
     */
    @GetMapping("/{id}/indicadores")
    @SecurityRequirements
    public ResponseEntity<IndicadoresDetalheResponse> buscarIndicadores(@PathVariable String id) {
        IndicadoresDetalheResponse detalhe = indicadorService.obterDetalhe(id);
        if (detalhe == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detalhe);
    }
}
