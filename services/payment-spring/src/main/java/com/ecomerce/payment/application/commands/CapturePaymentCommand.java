package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.dtos.CapturePaymentResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommand;
import java.util.UUID;

/**
 * Captura entero el importe de un cobro autorizado.
 */
public record CapturePaymentCommand(
        UUID id
) implements ReturningCommand<CapturePaymentResponseDto> {
}
