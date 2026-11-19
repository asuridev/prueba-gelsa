package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.DispatchQueuedMessagesCommand;
import com.ecomerce.notifications.application.commands.SendQueuedMessageCommand;
import com.ecomerce.notifications.application.interfaces.CommandHandler;
import com.ecomerce.notifications.application.port.out.CommandDispatcher;
import com.ecomerce.notifications.application.support.SweepPass;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.errors.MessageNotQueuedError;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Barrido de envío, cada minuto. Primero rescata los mensajes atascados en sending y los da por fallidos sin reenviarlos; después envía los queued más antiguos.
 *
 * <p>Se despacha sin transacción abarcadora: el reclamo del rescate confirma por su cuenta, cada save va en su
 * transacción y cada envío abre las suyas.
 */
@ApplicationComponent
public class DispatchQueuedMessagesCommandHandler implements CommandHandler<DispatchQueuedMessagesCommand> {

    private static final Logger log = LoggerFactory.getLogger(DispatchQueuedMessagesCommandHandler.class);

    /** «Hasta 200 mensajes queued» por ciclo, del diseño. */
    private static final int QUEUED_BATCH = 200;

    private final EmailMessageRepository emailMessageRepository;
    private final CommandDispatcher commandDispatcher;

    public DispatchQueuedMessagesCommandHandler(EmailMessageRepository emailMessageRepository, CommandDispatcher commandDispatcher) {
        this.emailMessageRepository = emailMessageRepository;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    @LogExceptions
    public void handle(DispatchQueuedMessagesCommand command) {
        rescueStalled();
        sendQueued();
    }

    /**
     * Rescate: lo que lleva en sending más que sendingTimeoutMinutes pasa a failed (stuck-in-sending) y publica
     * EmailDeliveryFailed. Nunca se reenvía: no se sabe si el correo llegó a salir. Fila a fila: una que pierde la
     * carrera con quien la saca de sending no corta el lote.
     */
    private void rescueStalled() {
        for (EmailMessage stalled : emailMessageRepository.claimForStalledDispatchQueuedMessages()) {
            try {
                stalled.rescueStuck(Instant.now());
                emailMessageRepository.save(stalled);
            } catch (RuntimeException e) {
                log.atInfo().addKeyValue("keel.message_id", stalled.getId())
                        .log("Rescate omitido para el mensaje {}: {}", stalled.getId(), e.getClass().getSimpleName());
            }
        }
    }

    /** Toma hasta 200 queued por antigüedad y ejecuta sendQueuedMessage con cada uno, sin transacción abarcadora. */
    private void sendQueued() {
        List<UUID> ids = emailMessageRepository.findQueuedIdsForDispatch(QUEUED_BATCH);
        // Lote lleno: puede que queden más, y el tick repite la pasada (drenaje).
        SweepPass.claimed(ids.size(), QUEUED_BATCH);
        for (UUID id : ids) {
            try {
                commandDispatcher.dispatchWithoutTransaction(new SendQueuedMessageCommand(id));
            } catch (MessageNotQueuedError lostRace) {
                // Otra ejecución llegó antes: es el caso normal de un solapamiento, no un fallo.
            } catch (RuntimeException e) {
                // Un mensaje que falla no corta el lote: lo que quedó en sending lo rescata el siguiente ciclo.
                log.atWarn().addKeyValue("keel.message_id", id)
                        .log("El envío del mensaje {} falló: {}", id, e.getClass().getSimpleName());
            }
        }
    }
}
