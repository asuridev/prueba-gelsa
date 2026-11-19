package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import com.ecomerce.order.domain.enums.ApplyPaymentActionRejectedAction;
import com.ecomerce.order.domain.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Devuelve el pedido al estado que informa payment cuando la pasarela rechaza la anulación de su cobro.
 */
public record ApplyPaymentActionRejectedCommand(
        @NotBlank String chargeRequestId,
        @NotNull ApplyPaymentActionRejectedAction action,
        @NotNull PaymentStatus status
) implements Command {
}
