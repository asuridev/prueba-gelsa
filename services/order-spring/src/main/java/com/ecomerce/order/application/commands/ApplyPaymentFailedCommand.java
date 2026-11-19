package com.ecomerce.order.application.commands;

import com.ecomerce.order.application.interfaces.Command;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Marca el pedido con pago fallido cuando payment informa el fallo del cobro y avisa al comprador.
 */
public record ApplyPaymentFailedCommand(
        @NotBlank String chargeRequestId,
        @NotNull UUID paymentId,
        @NotNull PaymentFailureReason failureReason
) implements Command {
}
