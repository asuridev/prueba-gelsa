package com.ecomerce.notifications.domain.errors;

/**
 * La plantilla no tiene ninguna versión active.
 */
public class TemplateNotPublishedError extends ConflictException {

    public TemplateNotPublishedError(String message) {
        super(message, "TEMPLATE_NOT_PUBLISHED", 409, null);
    }
}
