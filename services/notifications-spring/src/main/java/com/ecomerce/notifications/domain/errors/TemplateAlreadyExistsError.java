package com.ecomerce.notifications.domain.errors;

/**
 * Ya existe una plantilla con ese code.
 */
public class TemplateAlreadyExistsError extends ConflictException {

    public TemplateAlreadyExistsError(String message) {
        super(message, "TEMPLATE_ALREADY_EXISTS", 409, null);
    }
}
