package com.ecomerce.order.application.dtos;

import com.ecomerce.order.application.support.RawJsonDeserializer;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateOrderResponseDto(
    UUID id,
    String customerRef,
    String customerEmail,
    OrderStatus status,
    String currency,
    BigDecimal totalAmount,
    Integer lineCount,
    UUID paymentId,
    @JsonRawValue @JsonDeserialize(using = RawJsonDeserializer.class) String customerAction,
    PaymentFailureReason failureReason,
    Instant createdAt,
    Instant updatedAt,
    List<OrderLineDto> lines
) {
}
