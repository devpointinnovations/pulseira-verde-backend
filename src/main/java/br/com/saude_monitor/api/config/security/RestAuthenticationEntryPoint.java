package br.com.saude_monitor.api.config.security;

import br.com.saude_monitor.api.config.exception.ApiError;
import br.com.saude_monitor.api.config.exception.CampoInvalido;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Devolve 401 ({@code NAO_AUTORIZADO}) no envelope padrão da API quando a autenticação
 * falha (token ausente/inválido/expirado), em vez da página de login padrão do Spring.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .code("NAO_AUTORIZADO")
                .message("Token ausente, inválido ou expirado.")
                .details(List.<CampoInvalido>of())
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
