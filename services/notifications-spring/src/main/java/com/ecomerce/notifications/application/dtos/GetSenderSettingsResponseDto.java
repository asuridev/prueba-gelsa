package com.ecomerce.notifications.application.dtos;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import java.util.UUID;

public record GetSenderSettingsResponseDto(
    UUID id,
    SenderSettingsKey key,
    String senderAddress,
    String senderName,
    String replyToAddress
) {
}
