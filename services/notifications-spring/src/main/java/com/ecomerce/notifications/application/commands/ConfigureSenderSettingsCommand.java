package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.ConfigureSenderSettingsResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Crea o reemplaza el remitente del servicio. Los mensajes ya aceptados conservan el remitente que congelaron.
 */
public record ConfigureSenderSettingsCommand(
        // El @Pattern del value type EmailAddress (^[^@\s]+@[^@\s]+\.[^@\s]+$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con EmailAddressFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String senderAddress,
        @Size(max = 120) String senderName,
        // El @Pattern del value type EmailAddress (^[^@\s]+@[^@\s]+\.[^@\s]+$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con EmailAddressFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String replyToAddress
) implements ReturningCommand<ConfigureSenderSettingsResponseDto> {
}
