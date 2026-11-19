package com.ecomerce.payment.domain.idempotency;

import com.ecomerce.payment.domain.errors.ConflictException;

/**
 * La misma clave de idempotencia con un contenido DISTINTO.
 *
 * <p>No es la carrera ({@link IdempotencyConflictException}) ni el reintento normal. El
 * cliente prometió, al mandar la clave, que dos peticiones con ella son la misma petición;
 * esta no lo es. Las dos salidas posibles son deshonestas: reproducir la respuesta de la
 * primera contesta a una pregunta que este cliente no hizo, y ejecutar la segunda rompe la
 * promesa de la clave. Lo que queda es decírselo.
 *
 * <p>Se detecta comparando la firma del contenido (CommandSignature) con la que se guardó
 * junto a la clave — por eso el registro guarda una firma y no un flag.
 *
 * <p>El code es el CANÓNICO del framework (docs/framework-errors.md): el diseño no declara
 * uno propio, y eso es una respuesta legítima, no un hueco. Para cambiarlo, declara en los
 * errors de la operación un code de su familia con status 409.
 */
public class IdempotencyReuseException extends ConflictException {

    public IdempotencyReuseException(String scope, String idempotencyKey) {
        super(
                "La clave de idempotencia " + scope + "/" + idempotencyKey
                        + " ya se usó con un contenido distinto",
                "IDEMPOTENCY_KEY_REUSED",
                409,
                new Object[] {scope, idempotencyKey});
    }
}
