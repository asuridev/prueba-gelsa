package com.ecomerce.notifications.domain.errors;

/**
 * Falta alguna variable que la versión active declara required.
 */
public class MissingTemplateVariablesError extends BusinessException {

    public MissingTemplateVariablesError(String message) {
        super(message, "MISSING_TEMPLATE_VARIABLES", 422, null);
    }
}
