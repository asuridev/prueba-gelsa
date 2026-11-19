package com.ecomerce.notifications.application.mappers;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.dtos.CreateTemplateResponseDto;
import com.ecomerce.notifications.application.dtos.GetTemplateResponseDto;
import com.ecomerce.notifications.application.dtos.ListTemplatesResponseDto;
import com.ecomerce.notifications.application.dtos.RetireTemplateResponseDto;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;

@ApplicationComponent
public class EmailTemplateApplicationMapper {

    public CreateTemplateResponseDto toCreateTemplateResponseDto(EmailTemplate entity) {
        return new CreateTemplateResponseDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getHasActiveVersion());
    }

    public RetireTemplateResponseDto toRetireTemplateResponseDto(EmailTemplate entity) {
        return new RetireTemplateResponseDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getHasActiveVersion());
    }

    public GetTemplateResponseDto toGetTemplateResponseDto(EmailTemplate entity) {
        return new GetTemplateResponseDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getHasActiveVersion());
    }

    public ListTemplatesResponseDto toListTemplatesResponseDto(EmailTemplate entity) {
        return new ListTemplatesResponseDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getHasActiveVersion());
    }
}
