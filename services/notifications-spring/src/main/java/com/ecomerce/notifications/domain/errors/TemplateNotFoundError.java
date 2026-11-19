package com.ecomerce.notifications.domain.errors;

/**
 * No existe una plantilla con ese templateCode.
 */
public class TemplateNotFoundError extends NotFoundException {

    public TemplateNotFoundError(String message) {
        super(message, "TEMPLATE_NOT_FOUND", 404, null);
    }
}
