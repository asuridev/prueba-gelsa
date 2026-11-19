package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatus {

    QUEUED("queued"),
    SENDING("sending"),
    SENT("sent"),
    FAILED("failed");

    private final String value;

    MessageStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
