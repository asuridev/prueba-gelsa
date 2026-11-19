package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.ReleaseAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;

/**
 * Libera una dirección suprimida; el registro se conserva. Liberar una dirección ya released no cambia nada.
 */
public record ReleaseAddressCommand(
        String address
) implements ReturningCommand<ReleaseAddressResponseDto> {
}
