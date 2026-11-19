package com.ecomerce.notifications.domain.errors;

import java.util.Collections;
import java.util.List;

/**
 * Base de todos los errores de dominio: violaciones intencionales de reglas de
 * negocio que no deben reintentarse. Las excepciones de infraestructura
 * (timeouts de BD, conexión, etc.) NO extienden esta clase: se dejan como
 * RuntimeException para que puedan tratarse aparte (p. ej. reintentos).
 *
 * Lleva metadata estructurada opcional (code, httpStatus, args, details) que
 * ApiExceptionHandler usa para construir el ErrorResponse con codes estables.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final Integer httpStatus;
    private final Object[] args;
    private final List<String> details;

    protected DomainException() {
        this(null, null, null, null, null);
    }

    protected DomainException(String message) {
        this(message, null, null, null, null);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.httpStatus = null;
        this.args = null;
        this.details = null;
    }

    protected DomainException(String message, String code, Integer httpStatus, Object[] args) {
        this(message, code, httpStatus, args, null);
    }

    protected DomainException(String message, String code, Integer httpStatus, Object[] args, List<String> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.args = args;
        this.details = details == null ? null : Collections.unmodifiableList(details);
    }

    public String getCode() {
        return code;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Object[] getArgs() {
        return args;
    }

    public List<String> getDetails() {
        return details;
    }
}
