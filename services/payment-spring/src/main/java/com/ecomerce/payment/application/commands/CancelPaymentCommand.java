package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.dtos.CancelPaymentResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommand;
import java.util.UUID;

/**
 * Anula un cobro autorizado o que espera la autenticación del cliente, sin capturarlo.
 */
public record CancelPaymentCommand(
        UUID id
) implements ReturningCommand<CancelPaymentResponseDto> {
}
