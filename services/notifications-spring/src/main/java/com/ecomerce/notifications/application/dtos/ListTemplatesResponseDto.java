package com.ecomerce.notifications.application.dtos;

import java.util.UUID;

public record ListTemplatesResponseDto(
    UUID id,
    String code,
    String name,
    String description,
    Boolean hasActiveVersion
) {
}
