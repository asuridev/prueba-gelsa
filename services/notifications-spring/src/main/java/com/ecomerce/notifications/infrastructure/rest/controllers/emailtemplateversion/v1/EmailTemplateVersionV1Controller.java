package com.ecomerce.notifications.infrastructure.rest.controllers.emailtemplateversion.v1;

import com.ecomerce.notifications.application.commands.ActivateTemplateVersionCommand;
import com.ecomerce.notifications.application.commands.PublishTemplateVersionCommand;
import com.ecomerce.notifications.application.dtos.ActivateTemplateVersionResponseDto;
import com.ecomerce.notifications.application.dtos.GetTemplateVersionResponseDto;
import com.ecomerce.notifications.application.dtos.ListTemplateVersionsResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.dtos.PublishTemplateVersionResponseDto;
import com.ecomerce.notifications.application.queries.GetTemplateVersionQuery;
import com.ecomerce.notifications.application.queries.ListTemplateVersionsQuery;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "EmailTemplateVersion", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class EmailTemplateVersionV1Controller {

    /** El orden de listTemplateVersions cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_TEMPLATE_VERSIONS_ORDER = Sort.by(Sort.Order.desc("versionNumber"));
    /** Por qué puede ordenar el cliente listTemplateVersions (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_TEMPLATE_VERSIONS_SORTABLE = Map.of("versionNumber", "versionNumber", "publishedAt", "publishedAt");

    private final UseCaseMediator mediator;

    public EmailTemplateVersionV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Publica contenido nuevo para una plantilla. La versión nueva queda active y la que lo estaba pasa a archived, en la misma transacción.
     */
    @Operation(summary = "Publica contenido nuevo para una plantilla. La versión nueva queda active y la que lo estaba pasa a archived, en la misma transacción.")
    @PostMapping("/templates/{templateCode}/versions")
    public PublishTemplateVersionResponseDto publishTemplateVersion(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @Valid @RequestBody PublishTemplateVersionCommand command) {
        return mediator.dispatch(new PublishTemplateVersionCommand(
                templateCode,
                command.subject(),
                command.htmlBody(),
                command.textBody(),
                command.variables()));
    }

    /**
     * Vuelve a poner en active una versión archivada (revertir) y archiva la que estuviera active.
     */
    @Operation(summary = "Vuelve a poner en active una versión archivada (revertir) y archiva la que estuviera active.")
    @PostMapping("/templates/{templateCode}/versions/{versionNumber}/activate")
    public ActivateTemplateVersionResponseDto activateTemplateVersion(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @PathVariable @Min(1) Integer versionNumber) {
        return mediator.dispatch(new ActivateTemplateVersionCommand(templateCode, versionNumber));
    }

    /**
     * Devuelve una versión concreta de una plantilla, con su contenido completo.
     */
    @Operation(summary = "Devuelve una versión concreta de una plantilla, con su contenido completo.")
    @GetMapping("/templates/{templateCode}/versions/{versionNumber}")
    public GetTemplateVersionResponseDto getTemplateVersion(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @PathVariable @Min(1) Integer versionNumber) {
        return mediator.dispatch(new GetTemplateVersionQuery(templateCode, versionNumber));
    }

    /**
     * Historial de versiones de una plantilla, de la más nueva a la más antigua, sin los cuerpos.
     */
    @Operation(summary = "Historial de versiones de una plantilla, de la más nueva a la más antigua, sin los cuerpos.")
    @GetMapping("/templates/{templateCode}/versions")
    public PagedResponse<ListTemplateVersionsResponseDto> listTemplateVersions(@PathVariable @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListTemplateVersionsQuery(templateCode, QueryParameters.sorted(pageable, request, LIST_TEMPLATE_VERSIONS_SORTABLE, LIST_TEMPLATE_VERSIONS_ORDER)));
    }
}
