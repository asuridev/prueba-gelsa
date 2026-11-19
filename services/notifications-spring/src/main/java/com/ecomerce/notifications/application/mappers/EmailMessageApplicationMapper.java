package com.ecomerce.notifications.application.mappers;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.dtos.FindMessageByIdempotencyKeyResponseDto;
import com.ecomerce.notifications.application.dtos.GetMessageResponseDto;
import com.ecomerce.notifications.application.dtos.ListMessagesResponseDto;
import com.ecomerce.notifications.application.dtos.RequestNotificationResponseDto;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;

@ApplicationComponent
public class EmailMessageApplicationMapper {

    public RequestNotificationResponseDto toRequestNotificationResponseDto(EmailMessage entity) {
        return new RequestNotificationResponseDto(
                entity.getId(),
                entity.getRequestedBy(),
                entity.getIdempotencyKey(),
                entity.getRecipients(),
                entity.getRenderedSubject(),
                entity.getSender(),
                entity.getTemplateCode(),
                entity.getTemplateVersionNumber(),
                entity.getTemplateVersionId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getSendingSince(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getPersonalDataPurgedAt());
    }

    public GetMessageResponseDto toGetMessageResponseDto(EmailMessage entity) {
        return new GetMessageResponseDto(
                entity.getId(),
                entity.getRequestedBy(),
                entity.getIdempotencyKey(),
                entity.getRecipients(),
                entity.getRenderedSubject(),
                entity.getSender(),
                entity.getTemplateCode(),
                entity.getTemplateVersionNumber(),
                entity.getTemplateVersionId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getSendingSince(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getPersonalDataPurgedAt());
    }

    public ListMessagesResponseDto toListMessagesResponseDto(EmailMessage entity) {
        return new ListMessagesResponseDto(
                entity.getId(),
                entity.getRequestedBy(),
                entity.getIdempotencyKey(),
                entity.getRecipients(),
                entity.getRenderedSubject(),
                entity.getSender(),
                entity.getTemplateCode(),
                entity.getTemplateVersionNumber(),
                entity.getTemplateVersionId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getSendingSince(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getPersonalDataPurgedAt());
    }

    public FindMessageByIdempotencyKeyResponseDto toFindMessageByIdempotencyKeyResponseDto(EmailMessage entity) {
        return new FindMessageByIdempotencyKeyResponseDto(
                entity.getId(),
                entity.getRequestedBy(),
                entity.getIdempotencyKey(),
                entity.getRecipients(),
                entity.getRenderedSubject(),
                entity.getSender(),
                entity.getTemplateCode(),
                entity.getTemplateVersionNumber(),
                entity.getTemplateVersionId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getSendingSince(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getPersonalDataPurgedAt());
    }
}
