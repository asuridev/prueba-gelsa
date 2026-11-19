package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.dtos.SavePaymentMethodResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Guarda en el Wallet del titular el medio de pago que representa un token de la pasarela, para cobrarle después sin que esté presente.
 */
public record SavePaymentMethodCommand(
        @NotBlank @Size(min = 1, max = 200) String customerRef,
        @NotBlank @Size(max = 255) String paymentToken,
        Boolean makeDefault
) implements ReturningCommand<SavePaymentMethodResponseDto> {

    /**
     * Ámbito de la clave de idempotencia: la operación: sin idempotency.partitionBy la clave es GLOBAL entre llamantes, y eso lo decidió el diseño.
     * Es el primer argumento de IdempotencyStore.find/save; no lo compongas a mano.
     */
    public String idempotencyScope() {
        return "savePaymentMethod";
    }
}
