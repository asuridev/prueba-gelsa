package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.ListMessagesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.Query;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.data.domain.Pageable;

/**
 * Lista los mensajes con filtros, del más reciente al más antiguo.
 */
public record ListMessagesQuery(
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String requestedBy,
        MessageStatus status,
        // El @Pattern del value type TemplateCode (^[a-z][a-z0-9-]{2,47}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con TemplateCodeFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode,
        // El @Pattern del value type EmailAddress (^[^@\s]+@[^@\s]+\.[^@\s]+$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con EmailAddressFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String recipient,
        Instant requestedFrom,
        Instant requestedTo,
        Pageable pageable
) implements Query<PagedResponse<ListMessagesResponseDto>> {
}
