package com.ecomerce.notifications.application.dtos;

import com.ecomerce.notifications.domain.enums.SuppressionReason;
import com.ecomerce.notifications.domain.enums.SuppressionStatus;
import java.time.Instant;
import java.util.UUID;

public record GetSuppressedAddressResponseDto(
    UUID id,
    String address,
    SuppressionReason reason,
    String notes,
    SuppressionStatus status,
    Instant suppressedAt,
    Instant releasedAt
) {
}
