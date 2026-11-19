package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.ListTemplateVersionsResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.Query;
import org.springframework.data.domain.Pageable;

/**
 * Historial de versiones de una plantilla, de la más nueva a la más antigua, sin los cuerpos.
 */
public record ListTemplateVersionsQuery(
        String templateCode,
        Pageable pageable
) implements Query<PagedResponse<ListTemplateVersionsResponseDto>> {
}
