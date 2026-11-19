package com.ecomerce.notifications.infrastructure.rest.controllers.emailmessage.v1;

import com.ecomerce.notifications.application.commands.RequestNotificationCommand;
import com.ecomerce.notifications.application.dtos.FindMessageByIdempotencyKeyResponseDto;
import com.ecomerce.notifications.application.dtos.GetMessageResponseDto;
import com.ecomerce.notifications.application.dtos.ListMessagesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.dtos.RequestNotificationResponseDto;
import com.ecomerce.notifications.application.interfaces.EntryGate;
import com.ecomerce.notifications.application.queries.FindMessageByIdempotencyKeyQuery;
import com.ecomerce.notifications.application.queries.GetMessageQuery;
import com.ecomerce.notifications.application.queries.ListMessagesQuery;
import com.ecomerce.notifications.domain.enums.MessageStatus;
import com.ecomerce.notifications.infrastructure.configurations.security.CallerIdentity;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "EmailMessage", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class EmailMessageV1Controller {

    /** El orden de listMessages cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_MESSAGES_ORDER = Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.asc("id"));
    /** Por qué puede ordenar el cliente listMessages (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_MESSAGES_SORTABLE = Map.of("requestedAt", "requestedAt", "templateCode", "templateCode");

    private final UseCaseMediator mediator;

    public EmailMessageV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Acepta una petición de envío de un sistema llamante, la congela en un EmailMessage queued y responde sin prometer la entrega. Entra igual por HTTP y por el canal de eventos.
     */
    @Operation(summary = "Acepta una petición de envío de un sistema llamante, la congela en un EmailMessage queued y responde sin prometer la entrega. Entra igual por HTTP y por el canal de eventos.")
    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RequestNotificationResponseDto requestNotification(@Valid @RequestBody RequestNotificationCommand command) {
        return mediator.dispatch(new RequestNotificationCommand(
                CallerIdentity.resolve(),
                command.idempotencyKey(),
                command.templateCode(),
                command.recipients(),
                command.variables(),
                EntryGate.HTTP));
    }

    /**
     * Devuelve un mensaje por su id, sin los valores de las variables.
     */
    @Operation(summary = "Devuelve un mensaje por su id, sin los valores de las variables.")
    @GetMapping("/messages/{id}")
    public GetMessageResponseDto getMessage(@PathVariable UUID id) {
        return mediator.dispatch(new GetMessageQuery(id));
    }

    /**
     * Lista los mensajes con filtros, del más reciente al más antiguo.
     */
    @Operation(summary = "Lista los mensajes con filtros, del más reciente al más antiguo.")
    @GetMapping("/messages")
    public PagedResponse<ListMessagesResponseDto> listMessages(@RequestParam(required = false) @Size(max = 128) String requestedBy, @RequestParam(required = false) MessageStatus status, @RequestParam(required = false) @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @RequestParam(required = false) @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String recipient, @RequestParam(required = false) Instant requestedFrom, @RequestParam(required = false) Instant requestedTo, @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListMessagesQuery(requestedBy, status, templateCode, recipient, requestedFrom, requestedTo, QueryParameters.sorted(pageable, request, LIST_MESSAGES_SORTABLE, LIST_MESSAGES_ORDER)));
    }

    /**
     * Permite a un sistema llamante reconciliar el estado de una petición suya a partir de su idempotencyKey.
     */
    @Operation(summary = "Permite a un sistema llamante reconciliar el estado de una petición suya a partir de su idempotencyKey.")
    @GetMapping("/notifications/{idempotencyKey}")
    public FindMessageByIdempotencyKeyResponseDto findMessageByIdempotencyKey(@PathVariable @Size(min = 8, max = 128) @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$") String idempotencyKey) {
        return mediator.dispatch(new FindMessageByIdempotencyKeyQuery(idempotencyKey, CallerIdentity.resolve()));
    }
}
