package com.ecomerce.notifications.application.usecases;

import java.util.UUID;
import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.ListTemplateVersionsResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateVersionApplicationMapper;
import com.ecomerce.notifications.application.queries.ListTemplateVersionsQuery;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Historial de versiones de una plantilla, de la más nueva a la más antigua, sin los cuerpos.
 */
@ApplicationComponent
public class ListTemplateVersionsQueryHandler implements QueryHandler<ListTemplateVersionsQuery, PagedResponse<ListTemplateVersionsResponseDto>> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper;

    public ListTemplateVersionsQueryHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateVersionApplicationMapper = emailTemplateVersionApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListTemplateVersionsResponseDto> handle(ListTemplateVersionsQuery query) {
        EmailTemplate template = emailTemplateRepository.findByCode(query.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + query.templateCode()));
        UUID templateId = template.getId();
        return PagedResponse.from(emailTemplateRepository.listVersions(templateId, query.pageable())
                .map(version -> emailTemplateVersionApplicationMapper.toListTemplateVersionsResponseDto(version, templateId)));
    }
}
