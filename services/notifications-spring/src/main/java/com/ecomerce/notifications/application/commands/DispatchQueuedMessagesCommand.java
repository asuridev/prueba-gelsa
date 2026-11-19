package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.interfaces.Command;

/**
 * Barrido de envío, cada minuto. Primero rescata los mensajes atascados en sending y los da por fallidos sin reenviarlos; después envía los queued más antiguos.
 */
public record DispatchQueuedMessagesCommand() implements Command {
}
