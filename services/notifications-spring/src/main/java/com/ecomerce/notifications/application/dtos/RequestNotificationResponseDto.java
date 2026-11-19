package com.ecomerce.notifications.application.dtos;

import com.ecomerce.notifications.domain.enums.FailureReason;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.ecomerce.notifications.domain.valueobject.SenderSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestNotificationResponseDto(
    UUID id,
    String requestedBy,
    String idempotencyKey,
    List<String> recipients,
    String renderedSubject,
    SenderSnapshot sender,
    String templateCode,
    Integer templateVersionNumber,
    UUID templateVersionId,
    MessageStatus status,
    Instant requestedAt,
    Instant sendingSince,
    Instant sentAt,
    Instant failedAt,
    FailureReason failureReason,
    Instant personalDataPurgedAt
) {
}
