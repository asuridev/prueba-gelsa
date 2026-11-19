package com.ecomerce.notifications.domain.errors;

/**
 * Otra supresión de la misma dirección creó el registro a la vez.
 */
public class SuppressedAddressAlreadyExistsError extends ConflictException {

    public SuppressedAddressAlreadyExistsError(String message) {
        super(message, "SUPPRESSED_ADDRESS_ALREADY_EXISTS", 409, null);
    }
}
