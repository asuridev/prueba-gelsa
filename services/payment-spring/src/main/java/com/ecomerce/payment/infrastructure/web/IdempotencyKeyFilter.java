package com.ecomerce.payment.infrastructure.web;

import com.ecomerce.payment.application.support.IdempotencyContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lleva la cabecera Idempotency-Key al contexto de la petición.
 *
 * Se ordena justo detrás del filtro de correlación y por delante de la
 * seguridad: el contexto tiene que estar abierto antes de que el controller
 * construya el Command, y cerrado pase lo que pase (los hilos se reutilizan).
 *
 * No valida ni exige la cabecera: {@code keySource: client-key} dice de dónde
 * sale la clave, no que sea obligatoria. Sin cabecera, la operación se ejecuta
 * sin deduplicar, salvo que el diseño declare el error que la exige: entonces lo
 * rechaza el handler (conventions/mapping.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            IdempotencyContext.set(request.getHeader(HEADER));
            chain.doFilter(request, response);
        } finally {
            IdempotencyContext.clear();
        }
    }
}
