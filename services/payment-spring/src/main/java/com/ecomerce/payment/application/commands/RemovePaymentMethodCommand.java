package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.interfaces.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Retira un medio guardado del titular; deja de poder cobrarse.
 */
public record RemovePaymentMethodCommand(
        UUID paymentMethodId,
        @NotBlank @Size(min = 1, max = 200) String customerRef
) implements Command {
}
