package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SuppressionReason {

    HARD_BOUNCE("hard-bounce"),
    COMPLAINT("complaint"),
    MANUAL("manual");

    private final String value;

    SuppressionReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
