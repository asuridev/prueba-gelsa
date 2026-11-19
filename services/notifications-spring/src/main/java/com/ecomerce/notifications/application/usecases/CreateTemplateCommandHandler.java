package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.TemplateAlreadyExistsError;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.CreateTemplateCommand;
import com.ecomerce.notifications.application.dtos.CreateTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.EmailTemplateApplicationMapper;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;

/**
 * Da de alta una plantilla vacía, sin versiones; no se puede usar hasta publicar la primera.
 */
@ApplicationComponent
public class CreateTemplateCommandHandler implements ReturningCommandHandler<CreateTemplateCommand, CreateTemplateResponseDto> {

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateApplicationMapper emailTemplateApplicationMapper;

    public CreateTemplateCommandHandler(EmailTemplateRepository emailTemplateRepository, EmailTemplateApplicationMapper emailTemplateApplicationMapper) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailTemplateApplicationMapper = emailTemplateApplicationMapper;
    }

    @Override
    @LogExceptions
    public CreateTemplateResponseDto handle(CreateTemplateCommand command) {
        if (emailTemplateRepository.findByCode(command.code()).isPresent()) {
            throw new TemplateAlreadyExistsError("Ya existe una plantilla con el code " + command.code());
        }
        EmailTemplate template = EmailTemplate.create(command.code(), command.name(), command.description());
        return emailTemplateApplicationMapper.toCreateTemplateResponseDto(emailTemplateRepository.save(template));
    }
}
