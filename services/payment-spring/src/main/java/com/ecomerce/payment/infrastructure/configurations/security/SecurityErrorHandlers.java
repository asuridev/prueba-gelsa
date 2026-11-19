package com.ecomerce.payment.infrastructure.configurations.security;

import com.ecomerce.payment.infrastructure.rest.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Traduce los rechazos de la cadena de seguridad al ErrorResponse del contrato:
 * 401 cuando falta o no vale la credencial, 403 cuando la credencial es válida
 * pero no basta para la operación.
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "UNAUTHENTICATED", "Credenciales ausentes o no válidas");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden", "ACCESS_DENIED", "La credencial no autoriza esta operación");
    }

    // El code es el canónico de la lista cerrada (framework-errors.md): sin él un escenario
    // solo podía afirmar el status del rechazo.
    private void write(HttpServletResponse response, HttpStatus status, String error, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(status.value(), error, code, message, null));
    }
}
