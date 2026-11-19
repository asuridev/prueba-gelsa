package com.ecomerce.order.domain.idempotency;

import com.ecomerce.order.domain.errors.ConflictException;
import java.util.Map;

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
 * <p>El code sale del error que cada operación declara para este conflicto (createOrder);
 * las demás usan el CANÓNICO del framework (docs/framework-errors.md).
 */
public class IdempotencyReuseException extends ConflictException {

    /** El code que declara cada operación para este desenlace; la que no aparece usa el canónico. */
    private static final Map<String, String> DECLARED_CODES = Map.of("createOrder", "IDEMPOTENCY_KEY_REUSED");

    private static String codeFor(String scope) {
        int end = scope.indexOf(':');
        return DECLARED_CODES.getOrDefault(end < 0 ? scope : scope.substring(0, end), "IDEMPOTENCY_KEY_REUSED");
    }

    public IdempotencyReuseException(String scope, String idempotencyKey) {
        super(
                "La clave de idempotencia " + scope + "/" + idempotencyKey
                        + " ya se usó con un contenido distinto",
                codeFor(scope),
                409,
                new Object[] {scope, idempotencyKey});
    }
}
