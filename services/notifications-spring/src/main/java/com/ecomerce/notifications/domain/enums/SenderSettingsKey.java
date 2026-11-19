package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Clave constante que hace imposible un segundo SenderSettings, también ante dos altas simultáneas.
 */
public enum SenderSettingsKey {

    DEFAULT("default");

    private final String value;

    SenderSettingsKey(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
