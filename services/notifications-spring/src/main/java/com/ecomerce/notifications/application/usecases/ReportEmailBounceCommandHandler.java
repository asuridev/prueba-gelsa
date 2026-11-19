package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.ReportEmailBounceCommand;
import com.ecomerce.notifications.application.interfaces.CommandHandler;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import com.ecomerce.notifications.domain.enums.ReportEmailBounceBounceType;
import com.ecomerce.notifications.domain.enums.SuppressionReason;
import com.ecomerce.notifications.domain.errors.MessageNotFoundError;
import com.ecomerce.notifications.domain.errors.RecipientNotInMessageError;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El proveedor de correo notifica un rebote de un destinatario de un mensaje. Un rebote permanent suprime la dirección; uno transient solo se registra en el log.
 */
@ApplicationComponent
public class ReportEmailBounceCommandHandler implements CommandHandler<ReportEmailBounceCommand> {

    private static final Logger log = LoggerFactory.getLogger(ReportEmailBounceCommandHandler.class);

    /** Cota de SuppressedAddress.notes. */
    private static final int NOTES_MAX_LENGTH = 1000;

    private final SuppressedAddressRepository suppressedAddressRepository;
    private final EmailMessageRepository emailMessageRepository;

    public ReportEmailBounceCommandHandler(SuppressedAddressRepository suppressedAddressRepository, EmailMessageRepository emailMessageRepository) {
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.emailMessageRepository = emailMessageRepository;
    }

    @Override
    @LogExceptions
    public void handle(ReportEmailBounceCommand command) {
        EmailMessage message = emailMessageRepository.findById(command.messageId())
                .orElseThrow(() -> new MessageNotFoundError("No existe un mensaje con ese id"));
        String recipient = command.recipient().toLowerCase(Locale.ROOT);
        // Un mensaje ya purgado no conserva destinatarios: tampoco casa.
        if (!message.getRecipients().contains(recipient)) {
            throw new RecipientNotInMessageError("El destinatario no está entre los destinatarios del mensaje");
        }
        if (command.bounceType() == ReportEmailBounceBounceType.TRANSIENT) {
            // Solo el id del mensaje: la dirección y el diagnóstico son datos personales.
            log.atInfo()
                    .addKeyValue("keel.message_id", message.getId())
                    .addKeyValue("keel.bounce_type", "transient")
                    .log("Rebote transitorio registrado para el mensaje {}", message.getId());
            return;
        }
        // Permanente: el id del mensaje, un espacio y el diagnóstico, recortado por el final para que quepa.
        String notes = bounceNotes(message, command.diagnostic());
        Instant now = Instant.now();
        Optional<SuppressedAddress> existing = suppressedAddressRepository.findByAddress(recipient);
        if (existing.isEmpty()) {
            suppressedAddressRepository.save(SuppressedAddress.create(recipient, SuppressionReason.HARD_BOUNCE, notes, now));
        } else if (!existing.get().isActive()) {
            // Se reactiva aunque lo hubiera liberado una persona.
            SuppressedAddress suppression = existing.get();
            suppression.reactivate(SuppressionReason.HARD_BOUNCE, notes, now);
            suppressedAddressRepository.save(suppression);
        }
        // Ya active: no cambia nada.
    }

    private static String bounceNotes(EmailMessage message, String diagnostic) {
        String id = message.getId().toString();
        if (diagnostic == null || diagnostic.isEmpty()) {
            return id;
        }
        String prefix = id + " ";
        int room = NOTES_MAX_LENGTH - prefix.length();
        return prefix + (diagnostic.length() > room ? diagnostic.substring(0, room) : diagnostic);
    }
}
