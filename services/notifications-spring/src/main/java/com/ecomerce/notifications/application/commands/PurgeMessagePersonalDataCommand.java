package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.PurgeMessagePersonalDataResponseDto;
import com.ecomerce.notifications.application.interfaces.EntryGate;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Vacía los datos personales de los mensajes más antiguos que el plazo de retención (personalDataRetentionMonths) y conserva la fila y su clave para siempre. Diario a las 03:00 UTC y a demanda del administrador.
 */
public record PurgeMessagePersonalDataCommand(
        // La puerta por la que llegó este mensaje (keel-core/gen/entry-gates.js): la estampa quien lo construye,
        // el controller con HTTP. No se acepta del cuerpo.
        @JsonIgnore EntryGate entryGate
) implements ReturningCommand<PurgeMessagePersonalDataResponseDto> {
}
