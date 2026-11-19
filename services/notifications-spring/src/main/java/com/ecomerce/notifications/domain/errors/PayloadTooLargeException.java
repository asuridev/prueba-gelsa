package com.ecomerce.notifications.domain.errors;

import java.util.List;

/**
 * Errores de dominio que responden 413; ApiExceptionHandler la mapea.
 */
public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String message) {
        super(message);
    }

    public PayloadTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PayloadTooLargeException(String message, String code, Integer httpStatus, Object[] args) {
        super(message, code, httpStatus, args);
    }

    public PayloadTooLargeException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message, code, httpStatus, args, details);
    }
}
