package br.com.saude_monitor.api.user.dto;

import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;

/** Situação de uma finalidade específica. */
@Schema(name = "Finalidade")
@Builder
public record FinalidadeResponse(boolean aceito, Instant data, String versao) {

    static FinalidadeResponse de(ConsentimentoItem item) {
        return item == null
                ? FinalidadeResponse.builder().aceito(false).data(null).versao(null).build()
                : FinalidadeResponse.builder()
                        .aceito(item.isAceito())
                        .data(item.getData())
                        .versao(item.getVersao())
                        .build();
    }
}
