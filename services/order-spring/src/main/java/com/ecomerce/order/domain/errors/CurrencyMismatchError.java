package com.ecomerce.order.domain.errors;

/**
 * Los productos pedidos no comparten moneda.
 */
public class CurrencyMismatchError extends BusinessException {

    public CurrencyMismatchError(String message) {
        super(message, "CURRENCY_MISMATCH", 422, null);
    }
}
