package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.interfaces.Command;
import com.ecomerce.notifications.domain.enums.ReportEmailBounceBounceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * El proveedor de correo notifica un rebote de un destinatario de un mensaje. Un rebote permanent suprime la dirección; uno transient solo se registra en el log.
 */
public record ReportEmailBounceCommand(
        UUID messageId,
        // El @Pattern del value type EmailAddress (^[^@\s]+@[^@\s]+\.[^@\s]+$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con EmailAddressFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String recipient,
        @NotNull ReportEmailBounceBounceType bounceType,
        @Size(max = 1000) String diagnostic
) implements Command {
}
