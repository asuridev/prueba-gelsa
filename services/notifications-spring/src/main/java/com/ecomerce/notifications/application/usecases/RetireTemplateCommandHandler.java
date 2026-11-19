package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.RetireTemplateCommand;
import com.ecomerce.notifications.application.dtos.RetireTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateApplicationMapper;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Retira una plantilla archivando su versión active; conserva el historial y se puede volver a activar o publicar.
 */
@ApplicationComponent
public class RetireTemplateCommandHandler implements ReturningCommandHandler<RetireTemplateCommand, RetireTemplateResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateApplicationMapper emailTemplateApplicationMapper;

    public RetireTemplateCommandHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateApplicationMapper emailTemplateApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateApplicationMapper = emailTemplateApplicationMapper;
    }

    @Override
    @LogExceptions
    public RetireTemplateResponseDto handle(RetireTemplateCommand command) {
        EmailTemplate template = emailTemplateRepository.findByCode(command.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + command.templateCode()));
        template.retire();
        return emailTemplateApplicationMapper.toRetireTemplateResponseDto(emailTemplateRepository.save(template));
    }
}
