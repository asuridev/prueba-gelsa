package com.ecomerce.payment.domain.errors;

/**
 * Una invariante de la entidad rota por un valor que calculó el servidor (DSL 2.31): un check del diseño, la cota de un
 * campo o un derivado que no cabe en su tipo. La lanza ensureInvariants(), que el save llama antes de escribir nada.
 * El cliente no mandó nada inválido —eso es VALIDATION_ERROR, en el borde—: por eso 422.
 */
public class InvariantViolatedException extends BusinessException {

    public static final String CODE = "INVARIANT_VIOLATED";

    public InvariantViolatedException(String message) {
        super(message, CODE, 422, null);
    }
}
