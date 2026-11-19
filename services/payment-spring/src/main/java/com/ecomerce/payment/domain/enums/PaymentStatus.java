package com.ecomerce.payment.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {

    PENDING("pending"),
    ACTION_REQUIRED("actionRequired"),
    AUTHORIZED("authorized"),
    CAPTURING("capturing"),
    CANCELING("canceling"),
    CAPTURED("captured"),
    REFUNDING("refunding"),
    REFUNDED("refunded"),
    CANCELED("canceled"),
    FAILED("failed");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
