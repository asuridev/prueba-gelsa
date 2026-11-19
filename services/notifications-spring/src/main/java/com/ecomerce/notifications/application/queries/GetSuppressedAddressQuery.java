package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.GetSuppressedAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;

/**
 * Devuelve el registro de supresión de una dirección, esté active o released.
 */
public record GetSuppressedAddressQuery(
        String address
) implements Query<GetSuppressedAddressResponseDto> {
}
