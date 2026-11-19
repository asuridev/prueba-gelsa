package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.RetireTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;

/**
 * Retira una plantilla archivando su versión active; conserva el historial y se puede volver a activar o publicar.
 */
public record RetireTemplateCommand(
        String templateCode
) implements ReturningCommand<RetireTemplateResponseDto> {
}
