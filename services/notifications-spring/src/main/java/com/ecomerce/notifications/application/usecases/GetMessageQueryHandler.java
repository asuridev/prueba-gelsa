package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.MessageNotFoundError;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.GetMessageResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailMessageApplicationMapper;
import com.ecomerce.notifications.application.queries.GetMessageQuery;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;

/**
 * Devuelve un mensaje por su id, sin los valores de las variables.
 */
@ApplicationComponent
public class GetMessageQueryHandler implements QueryHandler<GetMessageQuery, GetMessageResponseDto> {

    private final EmailMessageRepository emailMessageRepository;
    private final EmailMessageApplicationMapper emailMessageApplicationMapper;

    public GetMessageQueryHandler(EmailMessageRepository emailMessageRepository, EmailMessageApplicationMapper emailMessageApplicationMapper) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailMessageApplicationMapper = emailMessageApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetMessageResponseDto handle(GetMessageQuery query) {
        EmailMessage message = emailMessageRepository.findById(query.id())
                .orElseThrow(() -> new MessageNotFoundError("No existe un mensaje con ese id"));
        return emailMessageApplicationMapper.toGetMessageResponseDto(message);
    }
}
