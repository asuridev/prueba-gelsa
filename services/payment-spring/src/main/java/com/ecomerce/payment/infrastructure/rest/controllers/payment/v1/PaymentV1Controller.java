package com.ecomerce.payment.infrastructure.rest.controllers.payment.v1;

import com.ecomerce.payment.application.commands.CancelPaymentCommand;
import com.ecomerce.payment.application.commands.CapturePaymentCommand;
import com.ecomerce.payment.application.commands.RefundPaymentCommand;
import com.ecomerce.payment.application.commands.RequestChargeCommand;
import com.ecomerce.payment.application.dtos.CancelPaymentResponseDto;
import com.ecomerce.payment.application.dtos.CapturePaymentResponseDto;
import com.ecomerce.payment.application.dtos.GetPaymentByChargeRequestResponseDto;
import com.ecomerce.payment.application.dtos.GetPaymentResponseDto;
import com.ecomerce.payment.application.dtos.ListPaymentsByCustomerResponseDto;
import com.ecomerce.payment.application.dtos.PagedResponse;
import com.ecomerce.payment.application.dtos.RefundPaymentResponseDto;
import com.ecomerce.payment.application.dtos.RequestChargeResponseDto;
import com.ecomerce.payment.application.queries.GetPaymentByChargeRequestQuery;
import com.ecomerce.payment.application.queries.GetPaymentQuery;
import com.ecomerce.payment.application.queries.ListPaymentsByCustomerQuery;
import com.ecomerce.payment.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.payment.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Payment", description = "Cobra con tarjeta, a través de una pasarela intercambiable, los importes que le piden otros servidores — autorizar, capturar, anular y devolver —, y guarda medios de pago para cobrar sin el cliente presente.")
public class PaymentV1Controller {

    /** El orden de listPaymentsByCustomer cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_PAYMENTS_BY_CUSTOMER_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    /** Por qué puede ordenar el cliente listPaymentsByCustomer (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_PAYMENTS_BY_CUSTOMER_SORTABLE = Map.of();

    private final UseCaseMediator mediator;

    public PaymentV1Controller(UseCaseMediator mediator) {
        this.mediator = mediator;
    }

    /**
     * Pide un cobro (autorización) con un token de un solo uso o con un medio guardado del titular. Registra el cobro en pending antes de llamar a la pasarela y responde con el cobro en el estado que haya alcanzado: authorized, actionRequired, failed, o pending si la pasarela no contestó.
     */
    @Operation(summary = "Pide un cobro (autorización) con un token de un solo uso o con un medio guardado del titular. Registra el cobro en pending antes de llamar a la pasarela y responde con el cobro en el estado que haya alcanzado: authorized, actionRequired, failed, o pending si la pasarela no contestó.")
    @PostMapping("/payments")
    public ResponseEntity<RequestChargeResponseDto> requestCharge(@Valid @RequestBody RequestChargeCommand command) {
        RequestChargeResponseDto response = mediator.dispatchWithoutTransaction(command);
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/payments/{id}").buildAndExpand(response.id()).toUri())
            .body(response);
    }

    /**
     * Captura entero el importe de un cobro autorizado.
     */
    @Operation(summary = "Captura entero el importe de un cobro autorizado.")
    @PostMapping("/payments/{id}/capture")
    public CapturePaymentResponseDto capturePayment(@PathVariable UUID id) {
        return mediator.dispatchWithoutTransaction(new CapturePaymentCommand(id));
    }

    /**
     * Anula un cobro autorizado o que espera la autenticación del cliente, sin capturarlo.
     */
    @Operation(summary = "Anula un cobro autorizado o que espera la autenticación del cliente, sin capturarlo.")
    @PostMapping("/payments/{id}/cancel")
    public CancelPaymentResponseDto cancelPayment(@PathVariable UUID id) {
        return mediator.dispatchWithoutTransaction(new CancelPaymentCommand(id));
    }

    /**
     * Devuelve todo o parte de lo que queda por devolver de un cobro capturado.
     */
    @Operation(summary = "Devuelve todo o parte de lo que queda por devolver de un cobro capturado.")
    @PostMapping("/payments/{id}/refunds")
    public RefundPaymentResponseDto refundPayment(@PathVariable UUID id, @Valid @RequestBody RefundPaymentCommand command) {
        return mediator.dispatchWithoutTransaction(new RefundPaymentCommand(id, command.refundRequestId(), command.amount(), command.reason()));
    }

    /**
     * Recupera un cobro por su id, con sus devoluciones.
     */
    @Operation(summary = "Recupera un cobro por su id, con sus devoluciones.")
    @GetMapping("/payments/{id}")
    public GetPaymentResponseDto getPayment(@PathVariable UUID id) {
        return mediator.dispatch(new GetPaymentQuery(id));
    }

    /**
     * Recupera un cobro por la clave de negocio con la que lo pidió el consumidor.
     */
    @Operation(summary = "Recupera un cobro por la clave de negocio con la que lo pidió el consumidor.")
    @GetMapping("/payments/by-charge-request/{chargeRequestId}")
    public GetPaymentByChargeRequestResponseDto getPaymentByChargeRequest(@PathVariable @Size(min = 1, max = 100) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String chargeRequestId) {
        return mediator.dispatch(new GetPaymentByChargeRequestQuery(chargeRequestId));
    }

    /**
     * Lista los cobros de un titular, del más reciente al más antiguo.
     */
    @Operation(summary = "Lista los cobros de un titular, del más reciente al más antiguo.")
    @GetMapping("/payments")
    public PagedResponse<ListPaymentsByCustomerResponseDto> listPaymentsByCustomer(@RequestParam @NotBlank @Size(min = 1, max = 200) String customerRef, @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListPaymentsByCustomerQuery(customerRef, QueryParameters.sorted(pageable, request, LIST_PAYMENTS_BY_CUSTOMER_SORTABLE, LIST_PAYMENTS_BY_CUSTOMER_ORDER)));
    }
}
