package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.SendQueuedMessageCommand;
import com.ecomerce.notifications.application.interfaces.CommandHandler;
import com.ecomerce.notifications.application.port.out.MailSender;
import com.ecomerce.notifications.application.port.out.TemplateRenderer;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.enums.FailureReason;
import com.ecomerce.notifications.domain.errors.MessageNotQueuedError;
import com.ecomerce.notifications.domain.mail.MailDeliveryException;
import com.ecomerce.notifications.domain.mail.MailMessage;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;
import com.ecomerce.notifications.domain.valueobject.SenderSnapshot;
import com.ecomerce.notifications.domain.valueobject.VariableValue;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envía un mensaje queued. Es la única operación que produce correo.
 *
 * <p>Se despacha SIN transacción abarcadora (la invoca el barrido): el reclamo queued → sending confirma en su
 * propia transacción antes de entregar el correo, y el desenlace se confirma después con su propio save. Un
 * correo que sale no lo deshace ningún rollback, y nada de lo que ocurre tras el reclamo se reintenta.
 */
@ApplicationComponent
public class SendQueuedMessageCommandHandler implements CommandHandler<SendQueuedMessageCommand> {

    private static final Logger log = LoggerFactory.getLogger(SendQueuedMessageCommandHandler.class);

    /** Cabecera propia con el id del mensaje: el adaptador SMTP lo usa como parte local del Message-ID. */
    private static final String NOTIFICATION_ID_HEADER = "X-Notification-Id";

    private final EmailMessageRepository emailMessageRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final SuppressedAddressRepository suppressedAddressRepository;
    private final MailSender mailSender;
    private final TemplateRenderer templateRenderer;

    public SendQueuedMessageCommandHandler(EmailMessageRepository emailMessageRepository,
            EmailTemplateRepository emailTemplateRepository,
            SuppressedAddressRepository suppressedAddressRepository,
            MailSender mailSender,
            TemplateRenderer templateRenderer) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
    }

    @Override
    @LogExceptions
    public void handle(SendQueuedMessageCommand command) {
        EmailMessage message = emailMessageRepository.findById(command.messageId())
                .filter(EmailMessage::isQueued)
                .orElseThrow(() -> new MessageNotQueuedError("El mensaje no existe o ya no está en queued"));

        // Supresión descubierta antes del reclamo: queued → failed, sin enviar nada. Si otra ejecución movió el
        // mensaje a la vez, el save pierde por versión y no publica nada.
        if (!suppressedAddressRepository.findActiveAddresses(message.getRecipients()).isEmpty()) {
            message.failBecauseRecipientSuppressed(Instant.now());
            emailMessageRepository.save(message);
            return;
        }

        // El reclamo confirma queued → sending en su propia transacción ANTES de entregar nada. Quien lo pierde
        // no envía.
        EmailMessage claimed = emailMessageRepository.claimForSendQueuedMessage(command.messageId())
                .orElseThrow(() -> new MessageNotQueuedError("El mensaje ya no está en queued"));

        FailureReason failure = null;
        boolean sent = false;
        try {
            mailSender.send(compose(claimed));
            sent = true;
        } catch (MailDeliveryException e) {
            if (e.partial()) {
                // Salió para al menos un destinatario: los rechazados llegan después como rebotes.
                sent = true;
            } else if (rejectedAll(e, claimed.getRecipients())) {
                failure = FailureReason.DELIVERY_REJECTED;
            } else {
                failure = FailureReason.DELIVERY_ERROR;
            }
        } catch (RuntimeException e) {
            // Falló antes de entregar nada (plantilla, composición): no se reintenta.
            log.atWarn().addKeyValue("keel.message_id", claimed.getId())
                    .log("El mensaje {} no se pudo componer: {}", claimed.getId(), e.getClass().getSimpleName());
            failure = FailureReason.DELIVERY_ERROR;
        }

        Instant now = Instant.now();
        if (sent) {
            claimed.markSent(now);
        } else {
            claimed.markDeliveryFailed(failure, now);
        }
        // Si el rescate ya cerró el mensaje como failed, este save pierde por versión: sigue failed y no se publica
        // EmailSent; se acepta ese desajuste a cambio de no reenviar nunca.
        emailMessageRepository.save(claimed);
    }

    /** ¿El relay rechazó a todos los destinatarios? Entonces es delivery-rejected; sin respuesta concluyente, delivery-error. */
    private static boolean rejectedAll(MailDeliveryException failure, List<String> recipients) {
        if (failure.rejected().isEmpty() || recipients.isEmpty()) {
            return false;
        }
        Set<String> rejected = failure.rejected().stream().map(a -> a.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return recipients.stream().allMatch(r -> rejected.contains(r.toLowerCase(Locale.ROOT)));
    }

    /** Un único correo html+text con todos los destinatarios en To, el remitente y el reply-to congelados. */
    private MailMessage compose(EmailMessage message) {
        EmailTemplate template = emailTemplateRepository.findByCode(message.getTemplateCode())
                .orElseThrow(() -> new IllegalStateException("La plantilla del mensaje ya no existe"));
        EmailTemplateVersion version = template.findVersionById(message.getTemplateVersionId())
                .orElseThrow(() -> new IllegalStateException("La versión congelada del mensaje ya no existe"));
        Map<String, Object> variables = new LinkedHashMap<>();
        for (VariableValue value : message.getVariableValues()) {
            variables.put(value.name(), value.value());
        }
        // La clave identifica contenido: la versión es inmutable, así que su id basta.
        String cacheKey = version.getId().toString();
        String html = templateRenderer.render(TemplateRenderer.Part.HTML, cacheKey, version.getHtmlBody(), variables);
        String text = templateRenderer.render(TemplateRenderer.Part.TEXT, cacheKey, version.getTextBody(), variables);
        SenderSnapshot sender = message.getSender();
        return new MailMessage(
                from(sender),
                sender.replyToAddress(),
                message.getRecipients(),
                List.of(),
                message.getRenderedSubject(),
                html,
                text,
                Map.of(NOTIFICATION_ID_HEADER, message.getId().toString()));
    }

    private static String from(SenderSnapshot sender) {
        if (sender.name() == null || sender.name().isBlank()) {
            return sender.address();
        }
        String quoted = sender.name().replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + quoted + "\" <" + sender.address() + ">";
    }
}
