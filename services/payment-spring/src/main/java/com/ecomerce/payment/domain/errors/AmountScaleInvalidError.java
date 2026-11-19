package com.ecomerce.payment.domain.errors;

/**
 * amount tiene más decimales que los de la moneda.
 */
public class AmountScaleInvalidError extends BadRequestException {

    public AmountScaleInvalidError(String message) {
        super(message, "AMOUNT_SCALE_INVALID", 400, null);
    }
}
