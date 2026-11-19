package com.ecomerce.payment.domain.idempotency;

import com.ecomerce.payment.domain.errors.ConflictException;

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
 * <p>El code es el CANÓNICO del framework (docs/framework-errors.md): el diseño no declara
 * uno propio, y eso es una respuesta legítima, no un hueco. Para cambiarlo, declara en los
 * errors de la operación un code de su familia con status 409.
 */
public class IdempotencyConflictException extends ConflictException {

    public IdempotencyConflictException(String scope, String idempotencyKey, Throwable cause) {
        // El constructor con metadata no acepta causa (DomainException guarda code y
        // status en campos finales), así que la causa se encadena aparte: perderla
        // dejaría el log sin el motivo real —qué constraint violó— justo en el fallo
        // que solo se reproduce bajo carga.
        super(
                "Otra petición con la misma clave de idempotencia está en curso: " + scope + "/" + idempotencyKey,
                "IDEMPOTENCY_KEY_IN_PROGRESS",
                409,
                new Object[] {scope, idempotencyKey});
        initCause(cause);
    }
}
