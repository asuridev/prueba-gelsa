package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.ListTemplatesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.Query;
import org.springframework.data.domain.Pageable;

/**
 * Lista las plantillas, opcionalmente filtradas por si tienen versión active.
 */
public record ListTemplatesQuery(
        Boolean hasActiveVersion,
        Pageable pageable
) implements Query<PagedResponse<ListTemplatesResponseDto>> {
}
