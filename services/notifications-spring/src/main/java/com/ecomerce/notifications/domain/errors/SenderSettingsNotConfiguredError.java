package com.ecomerce.notifications.domain.errors;

/**
 * Todavía no se ha configurado el remitente.
 */
public class SenderSettingsNotConfiguredError extends NotFoundException {

    public SenderSettingsNotConfiguredError(String message) {
        super(message, "SENDER_SETTINGS_NOT_CONFIGURED", 404, null);
    }
}
