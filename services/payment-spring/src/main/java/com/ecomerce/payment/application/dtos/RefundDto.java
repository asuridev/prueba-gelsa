package com.ecomerce.payment.application.dtos;

import com.ecomerce.payment.domain.enums.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundDto(
    UUID id,
    String refundRequestId,
    BigDecimal amount,
    String reason,
    RefundStatus status,
    Instant createdAt,
    Instant updatedAt,
    UUID paymentId
) {
}
