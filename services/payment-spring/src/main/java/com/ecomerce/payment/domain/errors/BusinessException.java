package com.ecomerce.payment.domain.errors;

import java.util.List;

/**
 * Errores de dominio que responden 422; ApiExceptionHandler la mapea.
 */
public class BusinessException extends DomainException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(String message, String code, Integer httpStatus, Object[] args) {
        super(message, code, httpStatus, args);
    }

    public BusinessException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message, code, httpStatus, args, details);
    }
}
