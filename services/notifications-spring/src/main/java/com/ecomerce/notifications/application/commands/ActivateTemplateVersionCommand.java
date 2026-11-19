package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.ActivateTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;

/**
 * Vuelve a poner en active una versión archivada (revertir) y archiva la que estuviera active.
 */
public record ActivateTemplateVersionCommand(
        String templateCode,
        Integer versionNumber
) implements ReturningCommand<ActivateTemplateVersionResponseDto> {
}
