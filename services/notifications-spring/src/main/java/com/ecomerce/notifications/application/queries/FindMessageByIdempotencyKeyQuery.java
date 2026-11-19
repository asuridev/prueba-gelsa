package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.FindMessageByIdempotencyKeyResponseDto;
import com.ecomerce.notifications.application.interfaces.Query;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Permite a un sistema llamante reconciliar el estado de una petición suya a partir de su idempotencyKey.
 */
public record FindMessageByIdempotencyKeyQuery(
        String idempotencyKey,
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String requestedBy
) implements Query<FindMessageByIdempotencyKeyResponseDto> {
}
