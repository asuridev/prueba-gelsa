package com.ecomerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Motivo de fallo del cobro, con el vocabulario neutro que publica payment.
 */
public enum PaymentFailureReason {

    DECLINED("declined"),
    INSUFFICIENT_FUNDS("insufficientFunds"),
    EXPIRED_CARD("expiredCard"),
    AUTHENTICATION_FAILED("authenticationFailed"),
    FRAUD_SUSPECTED("fraudSuspected"),
    INVALID_PAYMENT_METHOD("invalidPaymentMethod"),
    NOT_RECEIVED("notReceived"),
    PROCESSING_ERROR("processingError");

    private final String value;

    PaymentFailureReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
