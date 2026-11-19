package com.ecomerce.notifications.domain.errors;

/**
 * No existe un mensaje con ese id.
 */
public class MessageNotFoundError extends NotFoundException {

    public MessageNotFoundError(String message) {
        super(message, "MESSAGE_NOT_FOUND", 404, null);
    }
}
