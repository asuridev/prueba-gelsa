package com.ecomerce.payment.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RefundStatus {

    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private final String value;

    RefundStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
