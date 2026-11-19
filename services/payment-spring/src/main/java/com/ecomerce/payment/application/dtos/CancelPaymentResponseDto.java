package com.ecomerce.payment.application.dtos;

import com.ecomerce.payment.application.support.RawJsonDeserializer;
import com.ecomerce.payment.domain.enums.FailureReason;
import com.ecomerce.payment.domain.enums.PaymentCancelOrigin;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CancelPaymentResponseDto(
    UUID id,
    String chargeRequestId,
    String customerRef,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String gatewayPaymentId,
    Instant awaitingSince,
    FailureReason failureReason,
    @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) String customerAction,
    Instant actionRequiredSince,
    PaymentCancelOrigin cancelOrigin,
    UUID paymentMethodId,
    BigDecimal refundedAmount,
    Instant createdAt,
    Instant updatedAt,
    List<RefundDto> refunds
) {
}
