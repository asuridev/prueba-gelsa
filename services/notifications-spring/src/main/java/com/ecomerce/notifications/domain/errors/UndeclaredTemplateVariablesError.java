package com.ecomerce.notifications.domain.errors;

/**
 * subject, htmlBody o textBody usan una variable que no está en variables.
 */
public class UndeclaredTemplateVariablesError extends BusinessException {

    public UndeclaredTemplateVariablesError(String message) {
        super(message, "UNDECLARED_TEMPLATE_VARIABLES", 422, null);
    }
}
