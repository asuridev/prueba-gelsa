package com.ecomerce.payment.domain.errors;

/**
 * La pasarela ya había cancelado la autorización.
 */
public class AuthorizationExpiredError extends ConflictException {

    public AuthorizationExpiredError(String message) {
        super(message, "AUTHORIZATION_EXPIRED", 409, null);
    }
}
