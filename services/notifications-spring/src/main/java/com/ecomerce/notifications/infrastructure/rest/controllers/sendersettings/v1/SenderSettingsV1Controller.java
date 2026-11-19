package com.ecomerce.notifications.infrastructure.rest.controllers.sendersettings.v1;

import com.ecomerce.notifications.application.commands.ConfigureSenderSettingsCommand;
import com.ecomerce.notifications.application.dtos.ConfigureSenderSettingsResponseDto;
import com.ecomerce.notifications.application.dtos.GetSenderSettingsResponseDto;
import com.ecomerce.notifications.application.queries.GetSenderSettingsQuery;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "SenderSettings", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class SenderSettingsV1Controller {

    private final UseCaseMediator mediator;

    public SenderSettingsV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Crea o reemplaza el remitente del servicio. Los mensajes ya aceptados conservan el remitente que congelaron.
     */
    @Operation(summary = "Crea o reemplaza el remitente del servicio. Los mensajes ya aceptados conservan el remitente que congelaron.")
    @PutMapping("/sender-settings")
    public ConfigureSenderSettingsResponseDto configureSenderSettings(@Valid @RequestBody ConfigureSenderSettingsCommand command) {
        return mediator.dispatch(command);
    }

    /**
     * Devuelve el remitente configurado.
     */
    @Operation(summary = "Devuelve el remitente configurado.")
    @GetMapping("/sender-settings")
    public GetSenderSettingsResponseDto getSenderSettings() {
        return mediator.dispatch(new GetSenderSettingsQuery());
    }
}
