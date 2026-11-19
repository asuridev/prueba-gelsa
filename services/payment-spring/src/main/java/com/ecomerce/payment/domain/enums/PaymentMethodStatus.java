package com.ecomerce.payment.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethodStatus {

    ACTIVE("active"),
    REMOVED("removed");

    private final String value;

    PaymentMethodStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
