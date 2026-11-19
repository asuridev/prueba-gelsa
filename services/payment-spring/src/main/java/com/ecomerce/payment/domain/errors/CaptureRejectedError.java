package com.ecomerce.payment.domain.errors;

/**
 * La pasarela rechazó la captura por otro motivo.
 */
public class CaptureRejectedError extends BusinessException {

    public CaptureRejectedError(String message) {
        super(message, "CAPTURE_REJECTED", 422, null);
    }
}
