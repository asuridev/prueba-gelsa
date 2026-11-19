package com.ecomerce.notifications.infrastructure.rest.controllers.notifications.v1;

import com.ecomerce.notifications.application.commands.PurgeMessagePersonalDataCommand;
import com.ecomerce.notifications.application.dtos.PurgeMessagePersonalDataResponseDto;
import com.ecomerce.notifications.application.interfaces.EntryGate;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class NotificationsV1Controller {

    private final UseCaseMediator mediator;

    public NotificationsV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Vacía los datos personales de los mensajes más antiguos que el plazo de retención (personalDataRetentionMonths) y conserva la fila y su clave para siempre. Diario a las 03:00 UTC y a demanda del administrador.
     */
    @Operation(summary = "Vacía los datos personales de los mensajes más antiguos que el plazo de retención (personalDataRetentionMonths) y conserva la fila y su clave para siempre. Diario a las 03:00 UTC y a demanda del administrador.")
    @PostMapping("/maintenance/purge-personal-data")
    public PurgeMessagePersonalDataResponseDto purgeMessagePersonalData() {
        return mediator.dispatch(new PurgeMessagePersonalDataCommand(EntryGate.HTTP));
    }
}
