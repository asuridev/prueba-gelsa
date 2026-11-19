package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.MessageNotFoundError;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.FindMessageByIdempotencyKeyResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailMessageApplicationMapper;
import com.ecomerce.notifications.application.queries.FindMessageByIdempotencyKeyQuery;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;

/**
 * Permite a un sistema llamante reconciliar el estado de una petición suya a partir de su idempotencyKey.
 */
@ApplicationComponent
public class FindMessageByIdempotencyKeyQueryHandler implements QueryHandler<FindMessageByIdempotencyKeyQuery, FindMessageByIdempotencyKeyResponseDto> {

    private final EmailMessageRepository emailMessageRepository;
    private final EmailMessageApplicationMapper emailMessageApplicationMapper;

    public FindMessageByIdempotencyKeyQueryHandler(EmailMessageRepository emailMessageRepository, EmailMessageApplicationMapper emailMessageApplicationMapper) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailMessageApplicationMapper = emailMessageApplicationMapper;
    }

    @Override
    @LogExceptions
    public FindMessageByIdempotencyKeyResponseDto handle(FindMessageByIdempotencyKeyQuery query) {
        // Solo encuentra mensajes del propio llamante: requestedBy es parte de la clave.
        EmailMessage message = emailMessageRepository
                .findByRequestedByAndIdempotencyKey(query.requestedBy(), query.idempotencyKey())
                .orElseThrow(() -> new MessageNotFoundError("El llamante no tiene ningún mensaje con esa idempotencyKey"));
        return emailMessageApplicationMapper.toFindMessageByIdempotencyKeyResponseDto(message);
    }
}
