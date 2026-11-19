package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.InvalidTimeWindowError;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.ListMessagesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.EmailMessageApplicationMapper;
import com.ecomerce.notifications.application.queries.ListMessagesQuery;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;

/**
 * Lista los mensajes con filtros, del más reciente al más antiguo.
 */
@ApplicationComponent
public class ListMessagesQueryHandler implements QueryHandler<ListMessagesQuery, PagedResponse<ListMessagesResponseDto>> {

    private final EmailMessageRepository emailMessageRepository;
    private final EmailMessageApplicationMapper emailMessageApplicationMapper;

    public ListMessagesQueryHandler(EmailMessageRepository emailMessageRepository, EmailMessageApplicationMapper emailMessageApplicationMapper) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailMessageApplicationMapper = emailMessageApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListMessagesResponseDto> handle(ListMessagesQuery query) {
        if (query.requestedFrom() != null && query.requestedTo() != null
                && !query.requestedFrom().isBefore(query.requestedTo())) {
            throw new InvalidTimeWindowError("requestedFrom tiene que ser anterior a requestedTo");
        }
        // El finder cumple todos los filtros (recipient ignorando mayúsculas, ventana [from, to)) y el orden llega en el pageable.
        EmailMessageRepository.ListMessagesFilter filter = new EmailMessageRepository.ListMessagesFilter(
                query.requestedBy(), query.status(), query.templateCode(), query.recipient(),
                query.requestedFrom(), query.requestedTo());
        return PagedResponse.from(emailMessageRepository.search(filter, query.pageable())
                .map(emailMessageApplicationMapper::toListMessagesResponseDto));
    }
}
