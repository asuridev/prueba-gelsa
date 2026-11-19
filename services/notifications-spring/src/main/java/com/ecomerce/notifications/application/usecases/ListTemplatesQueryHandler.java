package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.ListTemplatesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateApplicationMapper;
import com.ecomerce.notifications.application.queries.ListTemplatesQuery;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Lista las plantillas, opcionalmente filtradas por si tienen versión active.
 */
@ApplicationComponent
public class ListTemplatesQueryHandler implements QueryHandler<ListTemplatesQuery, PagedResponse<ListTemplatesResponseDto>> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateApplicationMapper emailTemplateApplicationMapper;

    public ListTemplatesQueryHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateApplicationMapper emailTemplateApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateApplicationMapper = emailTemplateApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListTemplatesResponseDto> handle(ListTemplatesQuery query) {
        return PagedResponse.from(emailTemplateRepository
                .listTemplates(new EmailTemplateRepository.ListTemplatesFilter(query.hasActiveVersion()), query.pageable())
                .map(emailTemplateApplicationMapper::toListTemplatesResponseDto));
    }
}
