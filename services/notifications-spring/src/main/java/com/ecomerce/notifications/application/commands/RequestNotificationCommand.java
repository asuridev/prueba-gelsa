package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.RequestNotificationResponseDto;
import com.ecomerce.notifications.application.interfaces.EntryGate;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.ecomerce.notifications.domain.valueobject.VariableValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Acepta una petición de envío de un sistema llamante, la congela en un EmailMessage queued y responde sin prometer la entrega. Entra igual por HTTP y por el canal de eventos.
 */
public record RequestNotificationCommand(
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String requestedBy,
        // El @Pattern del value type IdempotencyKey (^[A-Za-z0-9._:-]{8,128}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con IdempotencyKeyFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Size(min = 8, max = 128) @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$") String idempotencyKey,
        // El @Pattern del value type TemplateCode (^[a-z][a-z0-9-]{2,47}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con TemplateCodeFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode,
        @NotEmpty @Size(min = 1, max = 20) List<@Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String> recipients,
        @Size(max = 50) @Valid List<VariableValue> variables,
        // La puerta por la que llegó este mensaje (keel-core/gen/entry-gates.js): la estampa quien lo construye,
        // el controller con HTTP. No se acepta del cuerpo.
        @JsonIgnore EntryGate entryGate
) implements ReturningCommand<RequestNotificationResponseDto> {

    // Una lista no informada es una lista vacía, nunca null (contrato del cable).
    public RequestNotificationCommand {
        recipients = recipients == null ? List.of() : recipients;
        variables = variables == null ? List.of() : variables;
    }
}
