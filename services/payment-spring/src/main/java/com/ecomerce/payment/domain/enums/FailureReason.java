package com.ecomerce.payment.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vocabulario neutro de Keel para el motivo de fallo de un cobro; cada pasarela se traduce a él.
 */
public enum FailureReason {

    DECLINED("declined"),
    INSUFFICIENT_FUNDS("insufficientFunds"),
    EXPIRED_CARD("expiredCard"),
    AUTHENTICATION_FAILED("authenticationFailed"),
    FRAUD_SUSPECTED("fraudSuspected"),
    INVALID_PAYMENT_METHOD("invalidPaymentMethod"),
    NOT_RECEIVED("notReceived"),
    PROCESSING_ERROR("processingError");

    private final String value;

    FailureReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
