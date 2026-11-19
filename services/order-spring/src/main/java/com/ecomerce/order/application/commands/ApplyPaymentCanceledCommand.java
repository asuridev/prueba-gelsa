package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Marca el pedido como cancelado cuando payment confirma que la autorización quedó anulada y avisa al comprador.
 */
public record ApplyPaymentCanceledCommand(
        @NotBlank String chargeRequestId,
        @NotNull UUID paymentId
) implements Command {
}
