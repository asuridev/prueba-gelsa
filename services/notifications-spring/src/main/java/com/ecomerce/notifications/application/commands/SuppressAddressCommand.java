package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.SuppressAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.ecomerce.notifications.domain.enums.SuppressionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Suprime una dirección a mano. Reactiva el registro si estaba released; si ya está active, no cambia nada.
 */
public record SuppressAddressCommand(
        String address,
        @NotNull SuppressionReason reason,
        @Size(max = 1000) String notes
) implements ReturningCommand<SuppressAddressResponseDto> {
}
