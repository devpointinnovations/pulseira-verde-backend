package br.com.saude_monitor.api.user.service;

/**
 * Quem envia o código de confirmação do e-mail depois que a conta é criada. O módulo {@code auth} implementa;
 * assim o cadastro, que é do {@code user}, não depende do {@code auth}.
 */
public interface EnvioDeConfirmacaoDeEmail {

    /**
     * Envia o código de 6 dígitos para confirmar o e-mail do cadastro (10/09/2026). Chamado pelo
     * {@code UserServiceImpl} logo após criar a conta.
     */
    void enviarCodigoConfirmacaoEmail(String email);
}
