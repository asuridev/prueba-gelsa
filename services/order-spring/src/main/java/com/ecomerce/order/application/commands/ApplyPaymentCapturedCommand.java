package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import jakarta.validation.constraints.NotBlank;

/**
 * Marca el pedido como pagado cuando payment confirma la captura y avisa al comprador.
 */
public record ApplyPaymentCapturedCommand(
        @NotBlank String chargeRequestId
) implements Command {
}
