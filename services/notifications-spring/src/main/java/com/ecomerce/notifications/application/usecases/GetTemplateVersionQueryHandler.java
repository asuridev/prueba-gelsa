package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateVersionNotFoundError;
import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.GetTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateVersionApplicationMapper;
import com.ecomerce.notifications.application.queries.GetTemplateVersionQuery;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Devuelve una versión concreta de una plantilla, con su contenido completo.
 */
@ApplicationComponent
public class GetTemplateVersionQueryHandler implements QueryHandler<GetTemplateVersionQuery, GetTemplateVersionResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper;

    public GetTemplateVersionQueryHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateVersionApplicationMapper = emailTemplateVersionApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetTemplateVersionResponseDto handle(GetTemplateVersionQuery query) {
        EmailTemplate template = emailTemplateRepository.findByCode(query.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + query.templateCode()));
        EmailTemplateVersion version = template.findVersion(query.versionNumber())
                .orElseThrow(() -> new TemplateVersionNotFoundError("La plantilla no tiene la versión " + query.versionNumber()));
        return emailTemplateVersionApplicationMapper.toGetTemplateVersionResponseDto(version, template.getId());
    }
}
