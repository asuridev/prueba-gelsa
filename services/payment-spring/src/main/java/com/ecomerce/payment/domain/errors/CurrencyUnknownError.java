package com.ecomerce.payment.domain.errors;

/**
 * currency no es un código vigente de ISO 4217.
 */
public class CurrencyUnknownError extends BadRequestException {

    public CurrencyUnknownError(String message) {
        super(message, "CURRENCY_UNKNOWN", 400, null);
    }
}
