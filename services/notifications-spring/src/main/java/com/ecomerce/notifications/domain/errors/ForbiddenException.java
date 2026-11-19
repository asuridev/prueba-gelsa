package com.ecomerce.notifications.domain.errors;

import java.util.List;

/**
 * Errores de dominio que responden 403; ApiExceptionHandler la mapea.
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForbiddenException(String message, String code, Integer httpStatus, Object[] args) {
        super(message, code, httpStatus, args);
    }

    public ForbiddenException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message, code, httpStatus, args, details);
    }
}
