package com.ecomerce.payment.domain.errors;

import java.util.List;

/**
 * Errores de dominio que responden 400; ApiExceptionHandler la mapea.
 */
public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadRequestException(String message, String code, Integer httpStatus, Object[] args) {
        super(message, code, httpStatus, args);
    }

    public BadRequestException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message, code, httpStatus, args, details);
    }
}
