package com.ecomerce.order.domain.idempotency;

import com.ecomerce.order.domain.errors.ConflictException;
import java.util.Map;

/**
 * Dos peticiones con la misma clave de idempotencia, a la vez.
 *
 * <p>No es el reintento normal: ese encuentra el registro ya commiteado y reproduce
 * la respuesta sin ejecutar nada. Esto es la ventana estrecha en la que la primera
 * petición aún no ha commiteado —y con varias réplicas, normalmente ni siquiera está
 * en este proceso—, así que no hay respuesta que reproducir todavía. La única
 * contestación honesta es «vuelve a intentarlo»: el registro guarda el id del recurso,
 * no el cuerpo, y hasta que la ganadora no commitee ese id no existe.
 *
 * <p>Lo que sí queda garantizado es lo importante: la transacción de quien pierde
 * revierte entera, así que de dos peticiones idénticas se ejecutó exactamente una.
 *
 * <p>El code sale del error que cada operación declara para este conflicto (createOrder);
 * las demás usan el CANÓNICO del framework (docs/framework-errors.md).
 */
public class IdempotencyConflictException extends ConflictException {

    /** El code que declara cada operación para este desenlace; la que no aparece usa el canónico. */
    private static final Map<String, String> DECLARED_CODES = Map.of("createOrder", "IDEMPOTENCY_KEY_IN_PROGRESS");

    private static String codeFor(String scope) {
        int end = scope.indexOf(':');
        return DECLARED_CODES.getOrDefault(end < 0 ? scope : scope.substring(0, end), "IDEMPOTENCY_KEY_IN_PROGRESS");
    }

    public IdempotencyConflictException(String scope, String idempotencyKey, Throwable cause) {
        // El constructor con metadata no acepta causa (DomainException guarda code y
        // status en campos finales), así que la causa se encadena aparte: perderla
        // dejaría el log sin el motivo real —qué constraint violó— justo en el fallo
        // que solo se reproduce bajo carga.
        super(
                "Otra petición con la misma clave de idempotencia está en curso: " + scope + "/" + idempotencyKey,
                codeFor(scope),
                409,
                new Object[] {scope, idempotencyKey});
        initCause(cause);
    }
}
