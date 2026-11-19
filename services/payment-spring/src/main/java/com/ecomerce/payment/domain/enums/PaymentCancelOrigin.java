package com.ecomerce.payment.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado del que salió el cobro al entrar en canceling; solo en canceling. Es a donde vuelve si la pasarela rechaza la anulación.
 */
public enum PaymentCancelOrigin {

    AUTHORIZED("authorized"),
    ACTION_REQUIRED("actionRequired");

    private final String value;

    PaymentCancelOrigin(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
