package com.ecomerce.order.domain.errors;

import java.util.List;

/**
 * Errores de dominio que responden 401; ApiExceptionHandler la mapea.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnauthorizedException(String message, String code, Integer httpStatus, Object[] args) {
        super(message, code, httpStatus, args);
    }

    public UnauthorizedException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message, code, httpStatus, args, details);
    }
}
