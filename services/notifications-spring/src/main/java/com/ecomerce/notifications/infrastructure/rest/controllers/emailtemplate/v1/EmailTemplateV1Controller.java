package com.ecomerce.notifications.infrastructure.rest.controllers.emailtemplate.v1;

import com.ecomerce.notifications.application.commands.CreateTemplateCommand;
import com.ecomerce.notifications.application.commands.RetireTemplateCommand;
import com.ecomerce.notifications.application.dtos.CreateTemplateResponseDto;
import com.ecomerce.notifications.application.dtos.GetTemplateResponseDto;
import com.ecomerce.notifications.application.dtos.ListTemplatesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.dtos.RetireTemplateResponseDto;
import com.ecomerce.notifications.application.queries.GetTemplateQuery;
import com.ecomerce.notifications.application.queries.ListTemplatesQuery;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "EmailTemplate", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class EmailTemplateV1Controller {

    /** El orden de listTemplates cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_TEMPLATES_ORDER = Sort.by(Sort.Order.asc("code"));
    /** Por qué puede ordenar el cliente listTemplates (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_TEMPLATES_SORTABLE = Map.of("code", "code", "name", "name");

    private final UseCaseMediator mediator;

    public EmailTemplateV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Da de alta una plantilla vacía, sin versiones; no se puede usar hasta publicar la primera.
     */
    @Operation(summary = "Da de alta una plantilla vacía, sin versiones; no se puede usar hasta publicar la primera.")
    @PostMapping("/templates")
    public CreateTemplateResponseDto createTemplate(@Valid @RequestBody CreateTemplateCommand command) {
        return mediator.dispatch(command);
    }

    /**
     * Retira una plantilla archivando su versión active; conserva el historial y se puede volver a activar o publicar.
     */
    @Operation(summary = "Retira una plantilla archivando su versión active; conserva el historial y se puede volver a activar o publicar.")
    @PostMapping("/templates/{templateCode}/retire")
    public RetireTemplateResponseDto retireTemplate(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode) {
        return mediator.dispatch(new RetireTemplateCommand(templateCode));
    }

    /**
     * Devuelve una plantilla sin su contenido.
     */
    @Operation(summary = "Devuelve una plantilla sin su contenido.")
    @GetMapping("/templates/{templateCode}")
    public GetTemplateResponseDto getTemplate(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode) {
        return mediator.dispatch(new GetTemplateQuery(templateCode));
    }

    /**
     * Lista las plantillas, opcionalmente filtradas por si tienen versión active.
     */
    @Operation(summary = "Lista las plantillas, opcionalmente filtradas por si tienen versión active.")
    @GetMapping("/templates")
    public PagedResponse<ListTemplatesResponseDto> listTemplates(@RequestParam(required = false) Boolean hasActiveVersion, @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListTemplatesQuery(hasActiveVersion, QueryParameters.sorted(pageable, request, LIST_TEMPLATES_SORTABLE, LIST_TEMPLATES_ORDER)));
    }
}
