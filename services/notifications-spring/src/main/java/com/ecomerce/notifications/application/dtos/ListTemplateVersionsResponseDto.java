package com.ecomerce.notifications.application.dtos;

import com.ecomerce.notifications.domain.enums.TemplateVersionStatus;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListTemplateVersionsResponseDto(
    UUID id,
    Integer versionNumber,
    String subject,
    List<TemplateVariable> variables,
    TemplateVersionStatus status,
    Instant publishedAt,
    UUID emailTemplateId
) {
}
