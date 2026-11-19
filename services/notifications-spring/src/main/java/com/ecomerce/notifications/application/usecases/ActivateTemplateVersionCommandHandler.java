package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateVersionNotFoundError;
import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.ActivateTemplateVersionCommand;
import com.ecomerce.notifications.application.dtos.ActivateTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateVersionApplicationMapper;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Vuelve a poner en active una versión archivada (revertir) y archiva la que estuviera active.
 */
@ApplicationComponent
public class ActivateTemplateVersionCommandHandler implements ReturningCommandHandler<ActivateTemplateVersionCommand, ActivateTemplateVersionResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper;

    public ActivateTemplateVersionCommandHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateVersionApplicationMapper emailTemplateVersionApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateVersionApplicationMapper = emailTemplateVersionApplicationMapper;
    }

    @Override
    @LogExceptions
    public ActivateTemplateVersionResponseDto handle(ActivateTemplateVersionCommand command) {
        EmailTemplate template = emailTemplateRepository.findByCode(command.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + command.templateCode()));
        EmailTemplateVersion target = template.findVersion(command.versionNumber())
                .orElseThrow(() -> new TemplateVersionNotFoundError("La plantilla no tiene la versión " + command.versionNumber()));
        // Ya active: no cambia nada y se devuelve.
        if (target.isActive()) {
            return emailTemplateVersionApplicationMapper.toActivateTemplateVersionResponseDto(target, template.getId());
        }
        // Una versión que no cumple la regla de marcadores no se reactiva: se rechaza antes de archivar la active.
        target.ensureWellFormedMarkers();
        // Relevo en dos pasos (ver publishTemplateVersion): archivar la active, volcar, y solo entonces activar.
        if (template.archiveActiveVersion()) {
            template = emailTemplateRepository.save(template);
            emailTemplateRepository.flushPendingWrites();
        }
        template.activateVersion(command.versionNumber());
        EmailTemplate saved = emailTemplateRepository.save(template);
        return emailTemplateVersionApplicationMapper.toActivateTemplateVersionResponseDto(
                saved.findVersion(command.versionNumber()).orElseThrow(), saved.getId());
    }
}
