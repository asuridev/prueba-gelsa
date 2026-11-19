package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.GetSenderSettingsResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;

/**
 * Devuelve el remitente configurado.
 */
public record GetSenderSettingsQuery() implements Query<GetSenderSettingsResponseDto> {
}
