package com.ecomerce.notifications.domain.errors;

/**
 * No existe ningún registro de supresión para esa dirección.
 */
public class SuppressedAddressNotFoundError extends NotFoundException {

    public SuppressedAddressNotFoundError(String message) {
        super(message, "SUPPRESSED_ADDRESS_NOT_FOUND", 404, null);
    }
}
