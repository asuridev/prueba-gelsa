package com.ecomerce.order.infrastructure.web;

import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Abre el contexto de correlación en cada petición HTTP.
 *
 * Toma el header X-Correlation-Id; si el cliente no lo envía, genera uno. Si lo envía
 * con un formato que no se acepta, CorrelationContext lo sustituye por uno nuevo. El valor
 * EFECTIVO se devuelve en la respuesta para que quien llamó pueda registrarlo y
 * rastrear después la petición en los logs y en los eventos que haya provocado.
 *
 * Se ordena casi al principio de la cadena para que hasta los fallos de
 * autenticación queden correlacionados.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            CorrelationContext.set(correlationId);
            // El efectivo, no el recibido: si no cumplía el formato, CorrelationContext puso otro.
            response.setHeader(HEADER, CorrelationContext.get());
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
