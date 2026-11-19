package com.ecomerce.notifications.infrastructure.rest.controllers.suppressedaddress.v1;

import com.ecomerce.notifications.application.commands.ReleaseAddressCommand;
import com.ecomerce.notifications.application.commands.ReportEmailBounceCommand;
import com.ecomerce.notifications.application.commands.SuppressAddressCommand;
import com.ecomerce.notifications.application.dtos.GetSuppressedAddressResponseDto;
import com.ecomerce.notifications.application.dtos.ListSuppressedAddressesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.dtos.ReleaseAddressResponseDto;
import com.ecomerce.notifications.application.dtos.SuppressAddressResponseDto;
import com.ecomerce.notifications.application.queries.GetSuppressedAddressQuery;
import com.ecomerce.notifications.application.queries.ListSuppressedAddressesQuery;
import com.ecomerce.notifications.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.notifications.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "SuppressedAddress", description = "Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.")
public class SuppressedAddressV1Controller {

    /** El orden de listSuppressedAddresses cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_SUPPRESSED_ADDRESSES_ORDER = Sort.by(Sort.Order.desc("suppressedAt"), Sort.Order.asc("id"));
    /** Por qué puede ordenar el cliente listSuppressedAddresses (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_SUPPRESSED_ADDRESSES_SORTABLE = Map.of("suppressedAt", "suppressedAt");

    private final UseCaseMediator mediator;

    public SuppressedAddressV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Suprime una dirección a mano. Reactiva el registro si estaba released; si ya está active, no cambia nada.
     */
    @Operation(summary = "Suprime una dirección a mano. Reactiva el registro si estaba released; si ya está active, no cambia nada.")
    @PutMapping("/suppressions/{address}")
    public SuppressAddressResponseDto suppressAddress(@PathVariable @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String address, @Valid @RequestBody SuppressAddressCommand command) {
        return mediator.dispatch(new SuppressAddressCommand(address, command.reason(), command.notes()));
    }

    /**
     * Libera una dirección suprimida; el registro se conserva. Liberar una dirección ya released no cambia nada.
     */
    @Operation(summary = "Libera una dirección suprimida; el registro se conserva. Liberar una dirección ya released no cambia nada.")
    @PostMapping("/suppressions/{address}/release")
    public ReleaseAddressResponseDto releaseAddress(@PathVariable @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String address) {
        return mediator.dispatch(new ReleaseAddressCommand(address));
    }

    /**
     * Devuelve el registro de supresión de una dirección, esté active o released.
     */
    @Operation(summary = "Devuelve el registro de supresión de una dirección, esté active o released.")
    @GetMapping("/suppressions/{address}")
    public GetSuppressedAddressResponseDto getSuppressedAddress(@PathVariable @Size(max = 254) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String address) {
        return mediator.dispatch(new GetSuppressedAddressQuery(address));
    }

    /**
     * Lista las direcciones con supresión active, de la más reciente a la más antigua.
     */
    @Operation(summary = "Lista las direcciones con supresión active, de la más reciente a la más antigua.")
    @GetMapping("/suppressions")
    public PagedResponse<ListSuppressedAddressesResponseDto> listSuppressedAddresses(@PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListSuppressedAddressesQuery(QueryParameters.sorted(pageable, request, LIST_SUPPRESSED_ADDRESSES_SORTABLE, LIST_SUPPRESSED_ADDRESSES_ORDER)));
    }

    /**
     * El proveedor de correo notifica un rebote de un destinatario de un mensaje. Un rebote permanent suprime la dirección; uno transient solo se registra en el log.
     */
    @Operation(summary = "El proveedor de correo notifica un rebote de un destinatario de un mensaje. Un rebote permanent suprime la dirección; uno transient solo se registra en el log.")
    @PostMapping("/messages/{messageId}/bounces")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportEmailBounce(@PathVariable UUID messageId, @Valid @RequestBody ReportEmailBounceCommand command) {
        mediator.dispatch(new ReportEmailBounceCommand(messageId, command.recipient(), command.bounceType(), command.diagnostic()));
    }
}
