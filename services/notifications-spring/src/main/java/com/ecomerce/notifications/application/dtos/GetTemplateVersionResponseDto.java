package com.ecomerce.notifications.application.dtos;

import com.ecomerce.notifications.domain.enums.TemplateVersionStatus;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GetTemplateVersionResponseDto(
    UUID id,
    Integer versionNumber,
    String subject,
    String htmlBody,
    String textBody,
    List<TemplateVariable> variables,
    TemplateVersionStatus status,
    Instant publishedAt,
    UUID emailTemplateId
) {
}
