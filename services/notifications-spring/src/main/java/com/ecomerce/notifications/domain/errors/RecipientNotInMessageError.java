package com.ecomerce.notifications.domain.errors;

/**
 * recipient no está entre los recipients del mensaje, incluido el caso de un mensaje ya purgado.
 */
public class RecipientNotInMessageError extends BusinessException {

    public RecipientNotInMessageError(String message) {
        super(message, "RECIPIENT_NOT_IN_MESSAGE", 422, null);
    }
}
