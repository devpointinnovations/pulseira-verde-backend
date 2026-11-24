package br.com.saude_monitor.api.user.dto;

import br.com.saude_monitor.api.user.document.ConsentimentoItem;
import br.com.saude_monitor.api.user.document.ConsentimentosDocument;

import java.time.Instant;

/**
 * Estado atual dos consentimentos do titular (E5-05 / LGPD).
 *
 * <p>Devolvido após a atualização para que o app reflita exatamente o que foi
 * gravado — inclusive a data do aceite/revogação, que serve de comprovante.</p>
 */
public record ConsentimentosResponse(
        FinalidadeResponse localizacao,
        FinalidadeResponse notificacoes,
        FinalidadeResponse termosUso
) {

    public static ConsentimentosResponse de(ConsentimentosDocument consentimentos) {
        if (consentimentos == null) {
            return new ConsentimentosResponse(
                    FinalidadeResponse.de(null), FinalidadeResponse.de(null), FinalidadeResponse.de(null));
        }
        return new ConsentimentosResponse(
                FinalidadeResponse.de(consentimentos.getLocalizacao()),
                FinalidadeResponse.de(consentimentos.getNotificacoes()),
                FinalidadeResponse.de(consentimentos.getTermosUso())
        );
    }
}
