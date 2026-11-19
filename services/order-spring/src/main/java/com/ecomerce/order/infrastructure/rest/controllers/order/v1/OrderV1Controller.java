package com.ecomerce.order.infrastructure.rest.controllers.order.v1;

import com.ecomerce.order.application.commands.CancelOrderCommand;
import com.ecomerce.order.application.commands.CreateOrderCommand;
import com.ecomerce.order.application.dtos.CancelOrderResponseDto;
import com.ecomerce.order.application.dtos.CreateOrderResponseDto;
import com.ecomerce.order.application.dtos.GetOrderResponseDto;
import com.ecomerce.order.application.dtos.ListMyOrdersResponseDto;
import com.ecomerce.order.application.dtos.PagedResponse;
import com.ecomerce.order.application.queries.GetOrderQuery;
import com.ecomerce.order.application.queries.ListMyOrdersQuery;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.infrastructure.configurations.security.CallerIdentity;
import com.ecomerce.order.infrastructure.configurations.security.ReadScopes;
import com.ecomerce.order.infrastructure.configurations.usecase.UseCaseMediator;
import com.ecomerce.order.infrastructure.rest.QueryParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Order", description = "Recibe los pedidos de los compradores, calcula su importe con la copia local del catálogo, pide el cobro a payment, sigue su desenlace y lo notifica al comprador a través de notifications.")
public class OrderV1Controller {

    /** El orden de listMyOrders cuando el cliente no manda ?sort= (el desempate por id lo añade el adaptador). */
    private static final Sort LIST_MY_ORDERS_ORDER = Sort.by(Sort.Order.desc("createdAt"));
    /** Por qué puede ordenar el cliente listMyOrders (output.sortable): ruta del contrato → propiedad del espejo. */
    private static final Map<String, String> LIST_MY_ORDERS_SORTABLE = Map.of("createdAt", "createdAt");

    private final UseCaseMediator mediator;
    private final ReadScopes readScopes;

    public OrderV1Controller(UseCaseMediator mediator, ReadScopes readScopes) {
        this.mediator = mediator;
        this.readScopes = readScopes;
    }

    /**
     * Crea un pedido con las líneas pedidas, calcula su importe con la copia local del catálogo y pide su cobro a payment, respondiendo con el pedido en el estado que alcanzó.
     */
    @Operation(summary = "Crea un pedido con las líneas pedidas, calcula su importe con la copia local del catálogo y pide su cobro a payment, respondiendo con el pedido en el estado que alcanzó.")
    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponseDto> createOrder(@Valid @RequestBody CreateOrderCommand command) {
        // Sin transacción abarcadora: el pedido se confirma ANTES de llamar a payment y se relee después (la llamada nunca va dentro de una transacción).
        CreateOrderResponseDto response = mediator.dispatchWithoutTransaction(new CreateOrderCommand(
                CallerIdentity.resolve(),
                CallerIdentity.claim("email"),
                command.lines(),
                command.paymentToken(),
                command.paymentMethodId()));
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/v1/orders/{id}").buildAndExpand(response.id()).toUri())
            .body(response);
    }

    /**
     * Devuelve un pedido del comprador que lo pide, con sus líneas y el estado de su cobro.
     */
    @Operation(summary = "Devuelve un pedido del comprador que lo pide, con sus líneas y el estado de su cobro.")
    @GetMapping("/orders/{id}")
    public GetOrderResponseDto getOrder(@PathVariable UUID id) {
        return mediator.dispatch(new GetOrderQuery(id, CallerIdentity.resolve(), readScopes.ownerOfOrder()));
    }

    /**
     * Lista los pedidos del comprador que lo pide, del más reciente al más antiguo.
     */
    @Operation(summary = "Lista los pedidos del comprador que lo pide, del más reciente al más antiguo.")
    @GetMapping("/orders")
    public PagedResponse<ListMyOrdersResponseDto> listMyOrders(@RequestParam(required = false) OrderStatus status, @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return mediator.dispatch(new ListMyOrdersQuery(CallerIdentity.resolve(), status, QueryParameters.sorted(pageable, request, LIST_MY_ORDERS_SORTABLE, LIST_MY_ORDERS_ORDER), readScopes.ownerOfOrder()));
    }

    /**
     * Anula un pedido que espera la autenticación del comprador (3DS) pidiendo a payment que anule su cobro; el pedido queda en canceling hasta que payment confirma el desenlace.
     */
    @Operation(summary = "Anula un pedido que espera la autenticación del comprador (3DS) pidiendo a payment que anule su cobro; el pedido queda en canceling hasta que payment confirma el desenlace.")
    @PostMapping("/orders/{id}/cancel")
    public CancelOrderResponseDto cancelOrder(@PathVariable UUID id) {
        // Sin transacción abarcadora: canceling se confirma ANTES de llamar a cancelPayment de payment.
        return mediator.dispatchWithoutTransaction(new CancelOrderCommand(id, CallerIdentity.resolve()));
    }
}
