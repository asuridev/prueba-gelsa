package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.GetTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;

/**
 * Devuelve una plantilla sin su contenido.
 */
public record GetTemplateQuery(
        String templateCode
) implements Query<GetTemplateResponseDto> {
}
