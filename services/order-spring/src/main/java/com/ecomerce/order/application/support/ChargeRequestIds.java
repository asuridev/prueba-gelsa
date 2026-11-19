package com.ecomerce.order.application.support;

import java.util.Optional;
import java.util.UUID;

/**
 * El chargeRequestId con el que order pide un cobro a payment: {@code "order:<id del pedido>"}.
 *
 * <p>Payment lo devuelve en sus eventos junto con los de otros clientes, así que un valor sin el
 * prefijo, o cuyo resto no es un UUID, no es de este servicio y se ignora sin error.
 */
public final class ChargeRequestIds {

    public static final String PREFIX = "order:";

    private ChargeRequestIds() {
    }

    public static String of(UUID orderId) {
        return PREFIX + orderId;
    }

    /** El id del pedido, o vacío si el valor no es de este servicio. */
    public static Optional<UUID> orderIdOf(String chargeRequestId) {
        if (chargeRequestId == null || !chargeRequestId.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(chargeRequestId.substring(PREFIX.length())));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }
}
