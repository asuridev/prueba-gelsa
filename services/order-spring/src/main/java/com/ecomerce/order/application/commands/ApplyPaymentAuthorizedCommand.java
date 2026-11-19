package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Registra que payment autorizó el cobro del pedido y pide su captura.
 */
public record ApplyPaymentAuthorizedCommand(
        @NotBlank String chargeRequestId,
        @NotNull UUID paymentId
) implements Command {
}
