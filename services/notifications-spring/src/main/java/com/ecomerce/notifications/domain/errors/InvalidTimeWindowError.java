package com.ecomerce.notifications.domain.errors;

/**
 * requestedFrom es posterior o igual a requestedTo.
 */
public class InvalidTimeWindowError extends BusinessException {

    public InvalidTimeWindowError(String message) {
        super(message, "INVALID_TIME_WINDOW", 422, null);
    }
}
