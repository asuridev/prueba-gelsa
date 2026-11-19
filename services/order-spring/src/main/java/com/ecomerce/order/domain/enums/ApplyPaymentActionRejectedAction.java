package com.ecomerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ApplyPaymentActionRejectedAction {

    CAPTURE("capture"),
    CANCEL("cancel"),
    REFUND("refund");

    private final String value;

    ApplyPaymentActionRejectedAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
