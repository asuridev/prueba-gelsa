package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.interfaces.Command;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Envía un mensaje queued. Es la única operación que produce correo.
 */
public record SendQueuedMessageCommand(
        @NotNull UUID messageId
) implements Command {
}
