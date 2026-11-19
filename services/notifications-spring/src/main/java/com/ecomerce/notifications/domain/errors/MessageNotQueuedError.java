package com.ecomerce.notifications.domain.errors;

/**
 * El mensaje no existe o ya no está en queued; el barrido lo salta sin hacer nada.
 */
public class MessageNotQueuedError extends BadRequestException {

    public MessageNotQueuedError(String message) {
        super(message, "MESSAGE_NOT_QUEUED", 400, null);
    }
}
