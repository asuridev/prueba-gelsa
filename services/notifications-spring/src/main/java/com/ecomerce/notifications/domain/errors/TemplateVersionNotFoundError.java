package com.ecomerce.notifications.domain.errors;

/**
 * La plantilla no tiene una versión con ese versionNumber.
 */
public class TemplateVersionNotFoundError extends NotFoundException {

    public TemplateVersionNotFoundError(String message) {
        super(message, "TEMPLATE_VERSION_NOT_FOUND", 404, null);
    }
}
