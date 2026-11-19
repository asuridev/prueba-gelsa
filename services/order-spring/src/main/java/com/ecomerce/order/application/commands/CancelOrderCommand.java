package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.dtos.CancelOrderResponseDto;
import com.ecomerce.order.application.interfaces.ReturningCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Anula un pedido que espera la autenticación del comprador (3DS) pidiendo a payment que anule su cobro; el pedido queda en canceling hasta que payment confirma el desenlace.
 */
public record CancelOrderCommand(
        UUID id,
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String customerRef
) implements ReturningCommand<CancelOrderResponseDto> {
}
