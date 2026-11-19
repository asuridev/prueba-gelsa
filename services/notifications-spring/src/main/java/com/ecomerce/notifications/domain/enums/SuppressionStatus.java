package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SuppressionStatus {

    ACTIVE("active"),
    RELEASED("released");

    private final String value;

    SuppressionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
