package com.ecomerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentActionRejectedAction {

    CAPTURE("capture"),
    CANCEL("cancel"),
    REFUND("refund");

    private final String value;

    PaymentActionRejectedAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
