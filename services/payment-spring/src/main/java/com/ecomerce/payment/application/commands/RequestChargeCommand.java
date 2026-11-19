package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.dtos.RequestChargeResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Pide un cobro (autorización) con un token de un solo uso o con un medio guardado del titular. Registra el cobro en pending antes de llamar a la pasarela y responde con el cobro en el estado que haya alcanzado: authorized, actionRequired, failed, o pending si la pasarela no contestó.
 */
public record RequestChargeCommand(
        @NotBlank @Size(min = 1, max = 100) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String chargeRequestId,
        @NotBlank @Size(min = 1, max = 200) String customerRef,
        @NotNull @DecimalMin("0.001") @Digits(integer = 16, fraction = 3) BigDecimal amount,
        // El @Pattern del value type CurrencyCode (^[A-Z]{3}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con CurrencyCodeFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 255) String paymentToken,
        UUID paymentMethodId
) implements ReturningCommand<RequestChargeResponseDto> {

    // constraints.scale — el importe se pone a su escala al entrar (con round se redondea; con reject solo se rellena).
    public RequestChargeCommand {
        if (amount != null) {
            amount = amount.setScale(3, RoundingMode.HALF_UP);
        }
    }
}
