package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.GetTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateApplicationMapper;
import com.ecomerce.notifications.application.queries.GetTemplateQuery;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Devuelve una plantilla sin su contenido.
 */
@ApplicationComponent
public class GetTemplateQueryHandler implements QueryHandler<GetTemplateQuery, GetTemplateResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateApplicationMapper emailTemplateApplicationMapper;

    public GetTemplateQueryHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateApplicationMapper emailTemplateApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateApplicationMapper = emailTemplateApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetTemplateResponseDto handle(GetTemplateQuery query) {
        EmailTemplate template = emailTemplateRepository.findByCode(query.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + query.templateCode()));
        return emailTemplateApplicationMapper.toGetTemplateResponseDto(template);
    }
}
