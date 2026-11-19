package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.GetTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;

/**
 * Devuelve una versión concreta de una plantilla, con su contenido completo.
 */
public record GetTemplateVersionQuery(
        String templateCode,
        Integer versionNumber
) implements Query<GetTemplateVersionResponseDto> {
}
