package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.PublishTemplateVersionCommand;
import com.ecomerce.notifications.application.dtos.PublishTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateVersionApplicationMapper;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Publica contenido nuevo para una plantilla. La versión nueva queda active y la que lo estaba pasa a archived, en la misma transacción.
 */
@ApplicationComponent
public class PublishTemplateVersionCommandHandler implements ReturningCommandHandler<PublishTemplateVersionCommand, PublishTemplateVersionResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper;

    public PublishTemplateVersionCommandHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateVersionApplicationMapper = emailTemplateVersionApplicationMapper;
    }

    @Override
    @LogExceptions
    public PublishTemplateVersionResponseDto handle(PublishTemplateVersionCommand command) {
        EmailTemplate template = emailTemplateRepository.findByCode(command.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + command.templateCode()));
        // La versión nueva se construye ANTES de tocar nada: valida variables repetidas, marcadores mal formados y
        // marcadores sin declarar, en ese orden.
        EmailTemplateVersion version = EmailTemplateVersion.create(template.nextVersionNumber(), command.subject(),
                command.htmlBody(), command.textBody(), command.variables());
        // Relevo en dos pasos: el índice único de la versión active se comprueba por fila, así que la anterior se
        // archiva y se vuelca a la base ANTES de insertar la nueva (el repositorio vuelca en cada save).
        if (template.archiveActiveVersion()) {
            template = emailTemplateRepository.save(template);
            emailTemplateRepository.flushPendingWrites();
        }
        template.addVersion(version);
        EmailTemplate saved = emailTemplateRepository.save(template);
        EmailTemplateVersion published = saved.findVersion(version.getVersionNumber()).orElseThrow();
        return emailTemplateVersionApplicationMapper.toPublishTemplateVersionResponseDto(published, saved.getId());
    }
}
