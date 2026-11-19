package com.ecomerce.notifications.application.mappers;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.dtos.ConfigureSenderSettingsResponseDto;
import com.ecomerce.notifications.application.dtos.GetSenderSettingsResponseDto;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;

@ApplicationComponent
public class SenderSettingsApplicationMapper {

    public ConfigureSenderSettingsResponseDto toConfigureSenderSettingsResponseDto(SenderSettings entity) {
        return new ConfigureSenderSettingsResponseDto(
                entity.getId(),
                entity.getKey(),
                entity.getSenderAddress(),
                entity.getSenderName(),
                entity.getReplyToAddress());
    }

    public GetSenderSettingsResponseDto toGetSenderSettingsResponseDto(SenderSettings entity) {
        return new GetSenderSettingsResponseDto(
                entity.getId(),
                entity.getKey(),
                entity.getSenderAddress(),
                entity.getSenderName(),
                entity.getReplyToAddress());
    }
}
