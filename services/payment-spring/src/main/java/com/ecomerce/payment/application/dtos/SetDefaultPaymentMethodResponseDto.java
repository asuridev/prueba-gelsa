package com.ecomerce.payment.application.dtos;

import com.ecomerce.payment.domain.enums.PaymentMethodStatus;
import com.ecomerce.payment.domain.valueobject.CardDetails;
import java.util.UUID;

public record SetDefaultPaymentMethodResponseDto(
    UUID id,
    CardDetails card,
    Boolean isDefault,
    PaymentMethodStatus status,
    Boolean expired,
    UUID walletId
) {
}
