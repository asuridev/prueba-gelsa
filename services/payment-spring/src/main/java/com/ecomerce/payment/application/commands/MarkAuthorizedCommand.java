package com.ecomerce.payment.application.commands;

import com.ecomerce.payment.application.interfaces.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registra que la pasarela autorizó el cobro.
 */
public record MarkAuthorizedCommand(
        // El @Pattern del value type RequestReference (^[A-Za-z0-9._:-]+$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con RequestReferenceFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Size(min = 1, max = 100) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String chargeRequestId,
        @NotBlank @Size(max = 255) String gatewayPaymentId
) implements Command {
}
