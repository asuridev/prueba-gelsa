package com.ecomerce.order.infrastructure.rest;

import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import java.time.Instant;
import java.util.List;

/**
 * Contrato de error de la API: body uniforme para todos los fallos.
 *
 * Los campos sin valor viajan como null (details fuera de una validación, code
 * en los errores de la cadena de seguridad). No se omiten: la forma del cuerpo
 * de error es contrato estable, y un consumidor que ve "details" siempre
 * presente no tiene que distinguir entre ausente y nulo.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String code, String message, List<String> details, String correlationId) {

    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, null, message, null, CorrelationContext.get());
    }

    public ErrorResponse(int status, String error, String code, String message, List<String> details) {
        this(Instant.now(), status, error, code, message, details, CorrelationContext.get());
    }
}
