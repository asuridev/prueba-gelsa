package com.ecomerce.notifications.domain.errors;

/**
 * variables declara dos veces el mismo name.
 */
public class DuplicateTemplateVariablesError extends BusinessException {

    public DuplicateTemplateVariablesError(String message) {
        super(message, "DUPLICATE_TEMPLATE_VARIABLES", 422, null);
    }
}
