package com.ecomerce.notifications.domain.aggregate;

import com.ecomerce.notifications.domain.enums.FailureReason;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.ecomerce.notifications.domain.errors.InvalidStateTransitionException;
import com.ecomerce.notifications.domain.errors.InvariantViolatedException;
import com.ecomerce.notifications.domain.errors.MessageNotQueuedError;
import com.ecomerce.notifications.domain.events.DomainEvent;
import com.ecomerce.notifications.domain.events.EmailDeliveryFailedEvent;
import com.ecomerce.notifications.domain.events.EmailSentEvent;
import com.ecomerce.notifications.domain.identity.Uuids;
import com.ecomerce.notifications.domain.valueobject.EmailAddressFormat;
import com.ecomerce.notifications.domain.valueobject.IdempotencyKeyFormat;
import com.ecomerce.notifications.domain.valueobject.SenderSnapshot;
import com.ecomerce.notifications.domain.valueobject.TemplateCodeFormat;
import com.ecomerce.notifications.domain.valueobject.VariableValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Petición de envío aceptada. Congela al aceptarla todo lo que el envío necesita, de modo que enviarla no depende de cambios posteriores en plantillas ni remitente.
 */
// Invariante (guardada en el factory y en los métodos de negocio): Mientras personalDataPurgedAt está vacío, recipients tiene entre 1 y 20 direcciones distintas y en minúsculas.
// Invariante (guardada en el factory y en los métodos de negocio): sender, templateCode, templateVersionNumber, templateVersionId, requestedBy e idempotencyKey no cambian tras aceptar el mensaje.
// Invariante (guardada en el factory y en los métodos de negocio): Con personalDataPurgedAt con valor, variableValues está vacío.
public class EmailMessage {

    // ─── Eventos de dominio ───────────────────────────────────────────────────
    // Se acumulan aquí y salen por pullDomainEvents() al persistir; nadie más
    // construye eventos de este agregado.
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // EmailSent lo emite markSent; EmailDeliveryFailed, cada método que lleva el mensaje a failed.

    protected void raise(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Vacía el buffer y devuelve lo acumulado; lo llama el adaptador de repositorio. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pending = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return pending;
    }

    private UUID id = Uuids.v7();

    // Identidad del sistema llamante (serviceClient por HTTP, metadata.source por evento).
    private String requestedBy;

    private String idempotencyKey;

    // Destinatarios deduplicados y en minúsculas. Se vacía al purgar.
    private List<String> recipients = new ArrayList<>();

    // Asunto renderizado al aceptar. Se vacía al purgar.
    private String renderedSubject;

    // Solo las variables declaradas. Se vacía al purgar.
    private List<VariableValue> variableValues = new ArrayList<>();

    private SenderSnapshot sender;

    private String templateCode;

    private Integer templateVersionNumber;

    private UUID templateVersionId;

    private MessageStatus status = MessageStatus.QUEUED;

    private Instant requestedAt = Instant.now();

    private Instant sendingSince;

    private Instant sentAt;

    private Instant failedAt;

    private FailureReason failureReason;

    private Instant personalDataPurgedAt;

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /**
     * Normaliza destinatarios: minúsculas y sin duplicados, conservando el orden de llegada y la primera
     * aparición de cada uno.
     */
    public static List<String> normalizeRecipients(List<String> raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (String recipient : raw) {
            if (recipient == null) {
                continue;
            }
            String lower = recipient.toLowerCase(java.util.Locale.ROOT);
            if (seen.add(lower)) {
                result.add(lower);
            }
        }
        return result;
    }

    /**
     * Factory de creación: acepta la petición y la deja en queued con todo lo que el envío necesita
     * congelado (remitente, versión de plantilla). Los destinatarios llegan ya normalizados.
     */
    public static EmailMessage accept(String requestedBy, String idempotencyKey, List<String> recipients,
            String renderedSubject, List<VariableValue> variableValues, SenderSnapshot sender, String templateCode,
            Integer templateVersionNumber, UUID templateVersionId, Instant now) {
        IdempotencyKeyFormat.validate(idempotencyKey);
        TemplateCodeFormat.validate(templateCode);
        List<String> normalized = normalizeRecipients(recipients);
        for (String recipient : normalized) {
            EmailAddressFormat.validate(recipient);
        }
        if (normalized.isEmpty() || normalized.size() > 20) {
            throw new InvariantViolatedException("EmailMessage: recipients tiene que traer entre 1 y 20 direcciones distintas.");
        }
        EmailMessage message = new EmailMessage(Uuids.v7(), requestedBy, idempotencyKey, normalized, renderedSubject,
                variableValues == null ? List.of() : variableValues, sender, templateCode, templateVersionNumber,
                templateVersionId, MessageStatus.QUEUED, now, null, null, null, null, null, null);
        return message;
    }

    /**
     * ¿La petición repetida es la misma que este mensaje? Mismo templateCode, mismo conjunto de destinatarios
     * normalizados y mismo conjunto de pares (name, value) de las variables declaradas. Un mensaje ya purgado
     * solo conserva el templateCode para comparar.
     *
     * @param declaredValues las variables de la petición, ya reducidas a las que declara la versión congelada
     */
    public boolean sameContentAs(String requestedTemplateCode, List<String> normalizedRecipients,
            List<VariableValue> declaredValues) {
        if (!templateCode.equals(requestedTemplateCode)) {
            return false;
        }
        if (personalDataPurgedAt != null) {
            return true;
        }
        return Set.copyOf(recipients).equals(Set.copyOf(normalizedRecipients))
                && Set.copyOf(variableValues).equals(Set.copyOf(declaredValues));
    }

    public boolean isQueued() {
        return status == MessageStatus.QUEUED;
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public EmailMessage(UUID id, String requestedBy, String idempotencyKey, List<String> recipients, String renderedSubject, List<VariableValue> variableValues, SenderSnapshot sender, String templateCode, Integer templateVersionNumber, UUID templateVersionId, MessageStatus status, Instant requestedAt, Instant sendingSince, Instant sentAt, Instant failedAt, FailureReason failureReason, Instant personalDataPurgedAt, Long lockVersion) {
        this.id = id;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.recipients = new ArrayList<>(recipients);
        this.renderedSubject = renderedSubject;
        this.variableValues = new ArrayList<>(variableValues);
        this.sender = sender;
        this.templateCode = templateCode;
        this.templateVersionNumber = templateVersionNumber;
        this.templateVersionId = templateVersionId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.sendingSince = sendingSince;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.personalDataPurgedAt = personalDataPurgedAt;
        this.lockVersion = lockVersion;
    }

    // Transiciones válidas del lifecycle del diseño; un estado con Set.of() es terminal.
    private static final Map<MessageStatus, Set<MessageStatus>> TRANSITIONS = Map.of(
        MessageStatus.QUEUED, Set.of(MessageStatus.SENDING, MessageStatus.FAILED),
        MessageStatus.SENDING, Set.of(MessageStatus.SENT, MessageStatus.FAILED),
        MessageStatus.SENT, Set.of(),
        MessageStatus.FAILED, Set.of()
    );

    // QUEUED → SENDING no tiene método aquí: la transición la hace el reclamo del puerto
    // (claimForSendQueuedMessage), en su propia transacción y antes de entregar el correo.

    /** QUEUED → FAILED (sendQueuedMessage): algún destinatario tiene ahora una supresión active; no se envía nada. */
    public void failBecauseRecipientSuppressed(Instant now) {
        if (status != MessageStatus.QUEUED) {
            throw new MessageNotQueuedError("El mensaje ya no está en queued");
        }
        failWith(FailureReason.RECIPIENT_SUPPRESSED, now);
    }

    /** SENDING → SENT (sendQueuedMessage): el relay aceptó el correo para al menos un destinatario. */
    public void markSent(Instant now) {
        transitionTo(MessageStatus.SENT);
        this.sentAt = now;
        raise(EmailSentEvent.of(id, requestedBy, idempotencyKey, templateCode, templateVersionNumber, sentAt));
    }

    /** SENDING → FAILED (sendQueuedMessage): el relay rechazó el correo (delivery-rejected) o el intento falló (delivery-error). */
    public void markDeliveryFailed(FailureReason reason, Instant now) {
        if (reason != FailureReason.DELIVERY_REJECTED && reason != FailureReason.DELIVERY_ERROR) {
            throw new IllegalArgumentException("Un fallo de entrega es delivery-rejected o delivery-error");
        }
        failWith(reason, now);
    }

    /** SENDING → FAILED (dispatchQueuedMessages): el rescate cierra un mensaje atascado sin reenviarlo. */
    public void rescueStuck(Instant now) {
        failWith(FailureReason.STUCK_IN_SENDING, now);
    }

    private void failWith(FailureReason reason, Instant now) {
        transitionTo(MessageStatus.FAILED);
        this.failureReason = reason;
        this.failedAt = now;
        raise(EmailDeliveryFailedEvent.of(id, requestedBy, idempotencyKey, templateCode, templateVersionNumber, failureReason, failedAt));
    }

    /**
     * Purga de datos personales (purgeMessagePersonalData): vacía recipients, renderedSubject y variableValues
     * y fija personalDataPurgedAt. Solo un mensaje terminal; repetirla sobre uno ya purgado no hace nada.
     */
    public boolean purgePersonalData(Instant now) {
        if (personalDataPurgedAt != null) {
            return false;
        }
        if (status != MessageStatus.SENT && status != MessageStatus.FAILED) {
            throw new InvariantViolatedException("EmailMessage: solo se purga un mensaje en sent o failed.");
        }
        this.recipients = new ArrayList<>();
        this.renderedSubject = null;
        this.variableValues = new ArrayList<>();
        this.personalDataPurgedAt = now;
        return true;
    }

    // Guard interno del lifecycle: lo llaman los métodos semánticos, nunca un handler.
    private void transitionTo(MessageStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        this.status = target;
    }

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private static final Pattern RECIPIENTS_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern TEMPLATE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{2,47}$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (requestedBy != null && (requestedBy.codePointCount(0, requestedBy.length()) > 128)) {
            throw new InvariantViolatedException("EmailMessage: requestedBy queda fuera de sus cotas.");
        }
        if (idempotencyKey != null && ((idempotencyKey.codePointCount(0, idempotencyKey.length()) < 8 || idempotencyKey.codePointCount(0, idempotencyKey.length()) > 128) || (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()))) {
            throw new InvariantViolatedException("EmailMessage: idempotencyKey queda fuera de sus cotas.");
        }
        if (recipients != null && (recipients.size() > 20)) {
            throw new InvariantViolatedException("EmailMessage: recipients queda fuera de sus cotas.");
        }
        if (recipients != null) {
            for (String element : recipients) {
                if (element != null && ((element.codePointCount(0, element.length()) > 254) || (!RECIPIENTS_PATTERN.matcher(element).matches()))) {
                    throw new InvariantViolatedException("EmailMessage: recipients queda fuera de sus cotas.");
                }
            }
        }
        if (renderedSubject != null && (renderedSubject.codePointCount(0, renderedSubject.length()) > 300)) {
            throw new InvariantViolatedException("EmailMessage: renderedSubject queda fuera de sus cotas.");
        }
        if (templateCode != null && (!TEMPLATE_CODE_PATTERN.matcher(templateCode).matches())) {
            throw new InvariantViolatedException("EmailMessage: templateCode queda fuera de sus cotas.");
        }
        // Sus checks (domain: entities.EmailMessage.checks).
        // check 0: exactamente uno de recipients, personalDataPurgedAt.
        if (Stream.of(!(isAbsent(recipients)), !(personalDataPurgedAt == null)).filter(Boolean::booleanValue).count() != 1) {
            throw new InvariantViolatedException("EmailMessage: exactamente uno de recipients, personalDataPurgedAt.");
        }
        // check 1: exactamente uno de renderedSubject, personalDataPurgedAt.
        if (Stream.of(!(isAbsent(renderedSubject)), !(personalDataPurgedAt == null)).filter(Boolean::booleanValue).count() != 1) {
            throw new InvariantViolatedException("EmailMessage: exactamente uno de renderedSubject, personalDataPurgedAt.");
        }
        // check 2: sentAt es obligatorio cuando status es sent.
        if (status == MessageStatus.SENT && (sentAt == null)) {
            throw new InvariantViolatedException("EmailMessage: sentAt es obligatorio cuando status es sent.");
        }
        // check 3: failureReason es obligatorio cuando status es failed.
        if (status == MessageStatus.FAILED && (failureReason == null)) {
            throw new InvariantViolatedException("EmailMessage: failureReason es obligatorio cuando status es failed.");
        }
        // check 4: failedAt es obligatorio cuando status es failed.
        if (status == MessageStatus.FAILED && (failedAt == null)) {
            throw new InvariantViolatedException("EmailMessage: failedAt es obligatorio cuando status es failed.");
        }
        // check 5: sendingSince es obligatorio cuando status es sending o sent.
        if ((status == MessageStatus.SENDING || status == MessageStatus.SENT) && (sendingSince == null)) {
            throw new InvariantViolatedException("EmailMessage: sendingSince es obligatorio cuando status es sending o sent.");
        }
        // check 6: sendingSince tiene que estar vacío cuando status es queued.
        if (status == MessageStatus.QUEUED && !(sendingSince == null)) {
            throw new InvariantViolatedException("EmailMessage: sendingSince tiene que estar vacío cuando status es queued.");
        }
        // check 7: sentAt tiene que estar vacío cuando status es queued.
        if (status == MessageStatus.QUEUED && !(sentAt == null)) {
            throw new InvariantViolatedException("EmailMessage: sentAt tiene que estar vacío cuando status es queued.");
        }
        // check 8: failedAt tiene que estar vacío cuando status es queued.
        if (status == MessageStatus.QUEUED && !(failedAt == null)) {
            throw new InvariantViolatedException("EmailMessage: failedAt tiene que estar vacío cuando status es queued.");
        }
        // check 9: failureReason tiene que estar vacío cuando status es queued.
        if (status == MessageStatus.QUEUED && !(failureReason == null)) {
            throw new InvariantViolatedException("EmailMessage: failureReason tiene que estar vacío cuando status es queued.");
        }
        // check 10: sendingSince es obligatorio cuando failureReason es delivery-rejected o delivery-error o stuck-in-sending.
        if ((failureReason == FailureReason.DELIVERY_REJECTED || failureReason == FailureReason.DELIVERY_ERROR || failureReason == FailureReason.STUCK_IN_SENDING) && (sendingSince == null)) {
            throw new InvariantViolatedException("EmailMessage: sendingSince es obligatorio cuando failureReason es delivery-rejected o delivery-error o stuck-in-sending.");
        }
    }

    private static boolean isAbsent(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    private static boolean isAbsent(String value) {
        return value == null || value.isEmpty();
    }

    public UUID getId() {
        return id;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<String> getRecipients() {
        return List.copyOf(recipients);
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public List<VariableValue> getVariableValues() {
        return List.copyOf(variableValues);
    }

    public SenderSnapshot getSender() {
        return sender;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Integer getTemplateVersionNumber() {
        return templateVersionNumber;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getSendingSince() {
        return sendingSince;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getPersonalDataPurgedAt() {
        return personalDataPurgedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
