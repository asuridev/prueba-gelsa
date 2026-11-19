package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.GetMessageResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;
import java.util.UUID;

/**
 * Devuelve un mensaje por su id, sin los valores de las variables.
 */
public record GetMessageQuery(
        UUID id
) implements Query<GetMessageResponseDto> {
}
