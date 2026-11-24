package br.com.saude_monitor.api.user.dto;

import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import br.com.saude_monitor.api.user.document.ConsentimentosDocument;

import java.time.Instant;
import lombok.Builder;

/**
 * Estado atual dos consentimentos do titular (E5-05 / LGPD).
 *
 * <p>Devolvido após a atualização para que o app reflita exatamente o que foi
 * gravado — inclusive a data do aceite/revogação, que serve de comprovante.</p>
 */
@Builder
public record ConsentimentosResponse(
        FinalidadeResponse localizacao,
        FinalidadeResponse notificacoes,
        FinalidadeResponse termosUso
) {

    public static ConsentimentosResponse de(ConsentimentosDocument consentimentos) {
        if (consentimentos == null) {
            return ConsentimentosResponse.builder()
                    .localizacao(FinalidadeResponse.de(null))
                    .notificacoes(FinalidadeResponse.de(null))
                    .termosUso(FinalidadeResponse.de(null))
                    .build();
        }
        return ConsentimentosResponse.builder()
                .localizacao(FinalidadeResponse.de(consentimentos.getLocalizacao()))
                .notificacoes(FinalidadeResponse.de(consentimentos.getNotificacoes()))
                .termosUso(FinalidadeResponse.de(consentimentos.getTermosUso()))
                .build();
    }
}
