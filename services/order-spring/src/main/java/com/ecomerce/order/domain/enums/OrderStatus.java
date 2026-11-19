package com.ecomerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado del pedido, que sigue el desenlace de su cobro en payment.
 */
public enum OrderStatus {

    PENDING_PAYMENT("pendingPayment"),
    ACTION_REQUIRED("actionRequired"),
    AUTHORIZED("authorized"),
    PAID("paid"),
    CANCELING("canceling"),
    CANCELED("canceled"),
    PAYMENT_FAILED("paymentFailed");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
