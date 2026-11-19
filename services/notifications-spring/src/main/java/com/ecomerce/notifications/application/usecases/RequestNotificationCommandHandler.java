package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.RequestNotificationCommand;
import com.ecomerce.notifications.application.dtos.RequestNotificationResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.EmailMessageApplicationMapper;
import com.ecomerce.notifications.application.port.out.TemplateRenderer;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.aggregate.EmailTemplate;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;
import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.errors.DuplicateTemplateVariablesError;
import com.ecomerce.notifications.domain.errors.EmptyRenderedSubjectError;
import com.ecomerce.notifications.domain.errors.IdempotencyKeyReusedError;
import com.ecomerce.notifications.domain.errors.MissingTemplateVariablesError;
import com.ecomerce.notifications.domain.errors.RecipientSuppressedError;
import com.ecomerce.notifications.domain.errors.RenderedSubjectTooLongError;
import com.ecomerce.notifications.domain.errors.SenderSettingsNotConfiguredError;
import com.ecomerce.notifications.domain.errors.TemplateNotFoundError;
import com.ecomerce.notifications.domain.errors.TemplateNotPublishedError;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import com.ecomerce.notifications.domain.repository.EmailTemplateRepository;
import com.ecomerce.notifications.domain.repository.SenderSettingsRepository;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;
import com.ecomerce.notifications.domain.valueobject.SenderSnapshot;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import com.ecomerce.notifications.domain.valueobject.VariableValue;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Acepta una petición de envío de un sistema llamante, la congela en un EmailMessage queued y responde sin prometer la entrega. Entra igual por HTTP y por el canal de eventos.
 */
@ApplicationComponent
public class RequestNotificationCommandHandler implements ReturningCommandHandler<RequestNotificationCommand, RequestNotificationResponseDto> {

    /** Cota del asunto renderizado, en caracteres. */
    private static final int SUBJECT_MAX_LENGTH = 300;

    private final EmailMessageRepository emailMessageRepository;
    private final EmailMessageApplicationMapper emailMessageApplicationMapper;
    private final SenderSettingsRepository senderSettingsRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final SuppressedAddressRepository suppressedAddressRepository;
    private final TemplateRenderer templateRenderer;

    public RequestNotificationCommandHandler(EmailMessageRepository emailMessageRepository,
            EmailMessageApplicationMapper emailMessageApplicationMapper,
            SenderSettingsRepository senderSettingsRepository,
            EmailTemplateRepository emailTemplateRepository,
            SuppressedAddressRepository suppressedAddressRepository,
            TemplateRenderer templateRenderer) {
        this.emailMessageRepository = emailMessageRepository;
        this.emailMessageApplicationMapper = emailMessageApplicationMapper;
        this.senderSettingsRepository = senderSettingsRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.templateRenderer = templateRenderer;
    }

    @Override
    @LogExceptions
    public RequestNotificationResponseDto handle(RequestNotificationCommand command) {
        // (1) Forma de la petición: dos valores para el mismo name son DUPLICATE_TEMPLATE_VARIABLES, antes de la repetición.
        Set<String> seenNames = new HashSet<>();
        Set<String> repeated = new TreeSet<>();
        for (VariableValue variable : command.variables()) {
            if (!seenNames.add(variable.name())) {
                repeated.add(variable.name());
            }
        }
        if (!repeated.isEmpty()) {
            throw new DuplicateTemplateVariablesError("variables trae más de un valor para: " + String.join(", ", repeated));
        }
        // Minúsculas y sin duplicados, en orden de llegada, antes de cualquier comparación.
        List<String> recipients = EmailMessage.normalizeRecipients(command.recipients());

        // (2) La repetición: la clave natural (requestedBy, idempotencyKey) es permanente y común a todas las puertas.
        Optional<EmailMessage> existing = emailMessageRepository
                .findByRequestedByAndIdempotencyKey(command.requestedBy(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command, recipients);
        }

        // (3) Remitente, plantilla, variables, asunto renderizado y supresiones, en ese orden.
        SenderSettings sender = senderSettingsRepository.findByKey(SenderSettingsKey.DEFAULT)
                .orElseThrow(() -> new SenderSettingsNotConfiguredError("Todavía no se ha configurado el remitente"));
        EmailTemplate template = emailTemplateRepository.findByCode(command.templateCode())
                .orElseThrow(() -> new TemplateNotFoundError("No existe una plantilla con el code " + command.templateCode()));
        EmailTemplateVersion version = template.activeVersion()
                .orElseThrow(() -> new TemplateNotPublishedError("La plantilla no tiene ninguna versión active"));

        // Las variables que la versión no declara se descartan en silencio y no se guardan.
        List<VariableValue> values = declaredValues(version, command.variables());
        Set<String> provided = new HashSet<>();
        values.forEach(v -> provided.add(v.name()));
        Set<String> missing = new TreeSet<>();
        for (TemplateVariable declared : version.getVariables()) {
            if (Boolean.TRUE.equals(declared.required()) && !provided.contains(declared.name())) {
                missing.add(declared.name());
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingTemplateVariablesError("Faltan variables obligatorias: " + String.join(", ", missing));
        }

        String subject = renderSubject(version, values);
        if (subject.isBlank()) {
            throw new EmptyRenderedSubjectError("El asunto renderizado queda vacío");
        }
        if (subject.codePointCount(0, subject.length()) > SUBJECT_MAX_LENGTH) {
            throw new RenderedSubjectTooLongError("El asunto renderizado pasa de " + SUBJECT_MAX_LENGTH + " caracteres");
        }

        // Una sola consulta para todos los destinatarios.
        if (!suppressedAddressRepository.findActiveAddresses(recipients).isEmpty()) {
            throw new RecipientSuppressedError("Algún destinatario tiene una supresión active");
        }

        EmailMessage message = EmailMessage.accept(
                command.requestedBy(),
                command.idempotencyKey(),
                recipients,
                subject,
                values,
                new SenderSnapshot(sender.getSenderAddress(), sender.getSenderName(), sender.getReplyToAddress()),
                template.getCode(),
                version.getVersionNumber(),
                version.getId(),
                Instant.now());
        // La carrera con otra petición de la misma clave la arbitra la constraint natural (409 IDEMPOTENCY_KEY_IN_PROGRESS).
        return emailMessageApplicationMapper.toRequestNotificationResponseDto(emailMessageRepository.save(message));
    }

    /** Misma clave: mismo contenido devuelve el mensaje existente sin tocar nada; distinto, IDEMPOTENCY_KEY_REUSED. */
    private RequestNotificationResponseDto replay(EmailMessage existing, RequestNotificationCommand command, List<String> recipients) {
        // Las variables se comparan sobre las que declara la versión CONGELADA del mensaje, no la active de hoy.
        Optional<EmailTemplateVersion> frozen = emailTemplateRepository.findByCode(existing.getTemplateCode())
                .flatMap(template -> template.findVersionById(existing.getTemplateVersionId()));
        List<VariableValue> comparable = frozen.isPresent()
                ? declaredValues(frozen.get(), command.variables())
                : command.variables().stream()
                        .filter(v -> existing.getVariableValues().stream().anyMatch(e -> e.name().equals(v.name())))
                        .toList();
        if (!existing.sameContentAs(command.templateCode(), recipients, comparable)) {
            throw new IdempotencyKeyReusedError("Ya existe un mensaje con esa idempotencyKey y distinto contenido");
        }
        return emailMessageApplicationMapper.toRequestNotificationResponseDto(existing);
    }

    private static List<VariableValue> declaredValues(EmailTemplateVersion version, List<VariableValue> variables) {
        Set<String> declared = new HashSet<>();
        version.getVariables().forEach(v -> declared.add(v.name()));
        return variables.stream().filter(v -> declared.contains(v.name())).toList();
    }

    /** El asunto se renderiza al aceptar; un salto de línea de un valor (\r\n, \n o \r) pasa a un espacio. */
    private String renderSubject(EmailTemplateVersion version, List<VariableValue> values) {
        Map<String, Object> variables = new LinkedHashMap<>();
        for (VariableValue value : values) {
            variables.put(value.name(), value.value().replaceAll("\r\n|\n|\r", " "));
        }
        return templateRenderer.render(TemplateRenderer.Part.SUBJECT, version.getId().toString(), version.getSubject(), variables);
    }
}
