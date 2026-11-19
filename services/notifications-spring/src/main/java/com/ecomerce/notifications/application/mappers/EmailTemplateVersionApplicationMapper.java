package com.ecomerce.notifications.application.mappers;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.dtos.ActivateTemplateVersionResponseDto;
import com.ecomerce.notifications.application.dtos.GetTemplateVersionResponseDto;
import com.ecomerce.notifications.application.dtos.ListTemplateVersionsResponseDto;
import com.ecomerce.notifications.application.dtos.PublishTemplateVersionResponseDto;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import java.util.UUID;

@ApplicationComponent
public class EmailTemplateVersionApplicationMapper {

    public PublishTemplateVersionResponseDto toPublishTemplateVersionResponseDto(EmailTemplateVersion entity, UUID emailTemplateId) {
        return new PublishTemplateVersionResponseDto(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getSubject(),
                entity.getHtmlBody(),
                entity.getTextBody(),
                entity.getVariables(),
                entity.getStatus(),
                entity.getPublishedAt(),
                emailTemplateId);
    }

    public ActivateTemplateVersionResponseDto toActivateTemplateVersionResponseDto(EmailTemplateVersion entity, UUID emailTemplateId) {
        return new ActivateTemplateVersionResponseDto(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getSubject(),
                entity.getHtmlBody(),
                entity.getTextBody(),
                entity.getVariables(),
                entity.getStatus(),
                entity.getPublishedAt(),
                emailTemplateId);
    }

    public GetTemplateVersionResponseDto toGetTemplateVersionResponseDto(EmailTemplateVersion entity, UUID emailTemplateId) {
        return new GetTemplateVersionResponseDto(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getSubject(),
                entity.getHtmlBody(),
                entity.getTextBody(),
                entity.getVariables(),
                entity.getStatus(),
                entity.getPublishedAt(),
                emailTemplateId);
    }

    public ListTemplateVersionsResponseDto toListTemplateVersionsResponseDto(EmailTemplateVersion entity, UUID emailTemplateId) {
        return new ListTemplateVersionsResponseDto(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getSubject(),
                entity.getVariables(),
                entity.getStatus(),
                entity.getPublishedAt(),
                emailTemplateId);
    }
}
