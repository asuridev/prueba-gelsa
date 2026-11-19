package com.ecomerce.order.domain.errors;

/**
 * El token del comprador no trae el claim email o su valor no es una dirección válida.
 */
public class CustomerEmailRequiredError extends BusinessException {

    public CustomerEmailRequiredError(String message) {
        super(message, "CUSTOMER_EMAIL_REQUIRED", 422, null);
    }
}
