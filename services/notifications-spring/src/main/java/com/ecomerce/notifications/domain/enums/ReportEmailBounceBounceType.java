package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportEmailBounceBounceType {

    PERMANENT("permanent"),
    TRANSIENT("transient");

    private final String value;

    ReportEmailBounceBounceType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
