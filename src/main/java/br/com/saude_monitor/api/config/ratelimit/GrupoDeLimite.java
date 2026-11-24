package br.com.saude_monitor.api.config.ratelimit;

public enum GrupoDeLimite {
    /** Rotas de autenticação sem outra identidade (cadastro, confirmação, senha), por IP. */
    AUTH,
    /** Rotas públicas, por usuário, instalação do app ou IP, nesta ordem. */
    PUBLICO,
    /** Login, por conta (e-mail), além do limite por IP do {@link #AUTH}. */
    LOGIN,
    /** Renovação de sessão, pelo próprio refresh token, nunca pelo IP. */
    REFRESH,
    /** Teto de abuso por IP, somando o tráfego limitado inteiro. */
    IP
}
