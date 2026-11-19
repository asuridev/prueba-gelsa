package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.dtos.SetDefaultPaymentMethodResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Marca un medio del titular como el preferente.
 */
public record SetDefaultPaymentMethodCommand(
        UUID paymentMethodId,
        @NotBlank @Size(min = 1, max = 200) String customerRef
) implements ReturningCommand<SetDefaultPaymentMethodResponseDto> {
}
