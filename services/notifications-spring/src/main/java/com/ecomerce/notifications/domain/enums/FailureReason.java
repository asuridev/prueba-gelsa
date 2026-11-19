package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Por qué un mensaje acabó en failed. delivery-rejected: el relay rechazó el mensaje; delivery-error: el intento falló sin respuesta concluyente; stuck-in-sending: el rescate lo cerró tras sendingTimeoutMinutes en sending, sin reenviarlo; recipient-suppressed: al ir a enviarlo, algún destinatario tenía una supresión active, y no se intentó.
 */
public enum FailureReason {

    DELIVERY_REJECTED("delivery-rejected"),
    DELIVERY_ERROR("delivery-error"),
    STUCK_IN_SENDING("stuck-in-sending"),
    RECIPIENT_SUPPRESSED("recipient-suppressed");

    private final String value;

    FailureReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
