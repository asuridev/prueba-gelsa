package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.CreateOrderCommand;
import com.ecomerce.order.application.dtos.CreateOrderResponseDto;
import com.ecomerce.order.application.interfaces.ReturningCommandHandler;
import com.ecomerce.order.application.mappers.OrderApplicationMapper;
import com.ecomerce.order.application.projection.ProductReader;
import com.ecomerce.order.application.support.ChargeRequestIds;
import com.ecomerce.order.application.support.CommandSignature;
import com.ecomerce.order.application.support.IdempotencyContext;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.aggregate.Product;
import com.ecomerce.order.domain.clients.PaymentClient;
import com.ecomerce.order.domain.clients.PaymentRejectedException;
import com.ecomerce.order.domain.clients.PaymentUnavailableException;
import com.ecomerce.order.domain.clients.RequestChargeResult;
import com.ecomerce.order.domain.entity.OrderLine;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import com.ecomerce.order.domain.errors.CurrencyMismatchError;
import com.ecomerce.order.domain.errors.CustomerEmailRequiredError;
import com.ecomerce.order.domain.errors.DuplicateOrderLineError;
import com.ecomerce.order.domain.errors.IdempotencyKeyRequiredError;
import com.ecomerce.order.domain.errors.PaymentSourceInvalidError;
import com.ecomerce.order.domain.errors.ProductNotAvailableError;
import com.ecomerce.order.domain.idempotency.IdempotencyConflictException;
import com.ecomerce.order.domain.idempotency.IdempotencyReuseException;
import com.ecomerce.order.domain.idempotency.IdempotencyStore;
import com.ecomerce.order.domain.identity.Uuids;
import com.ecomerce.order.domain.repository.OrderRepository;
import com.ecomerce.order.domain.valueobject.EmailAddressFormat;
import com.ecomerce.order.domain.valueobject.OrderLineRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Crea un pedido con las líneas pedidas, calcula su importe con la copia local del catálogo y pide su cobro a payment, respondiendo con el pedido en el estado que alcanzó.
 *
 * <p>Corre SIN transacción abarcadora (el controller despacha con dispatchWithoutTransaction): el pedido se
 * confirma en pendingPayment ANTES de llamar a payment, y la respuesta del cobro se aplica releyéndolo en una
 * segunda confirmación. La llamada a payment nunca ocurre dentro de una transacción.
 */
@ApplicationComponent
public class CreateOrderCommandHandler implements ReturningCommandHandler<CreateOrderCommand, CreateOrderResponseDto> {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderCommandHandler.class);

    /** Ventana de deduplicación declarada por el diseño (idempotency.ttlSeconds). */
    private static final long IDEMPOTENCY_TTL_SECONDS = 86400;

    /** Intentos de aplicar el desenlace del cobro cuando un evento de payment escribe el pedido a la vez. */
    private static final int SETTLE_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final OrderApplicationMapper orderApplicationMapper;
    private final ProductReader productReader;
    private final PaymentClient paymentClient;
    private final IdempotencyStore idempotencyStore;

    public CreateOrderCommandHandler(OrderRepository orderRepository, OrderApplicationMapper orderApplicationMapper, ProductReader productReader, PaymentClient paymentClient, IdempotencyStore idempotencyStore) {
        this.orderRepository = orderRepository;
        this.orderApplicationMapper = orderApplicationMapper;
        this.productReader = productReader;
        this.paymentClient = paymentClient;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    @LogExceptions
    public CreateOrderResponseDto handle(CreateOrderCommand command) {
        // El correo sale del claim email del token; sin él, o con un valor que no es una dirección, no hay pedido.
        String email = command.customerEmail();
        if (email == null || email.isBlank() || !EmailAddressFormat.matches(email)) {
            throw new CustomerEmailRequiredError("El token del comprador no trae un claim email válido");
        }

        String key = IdempotencyContext.get()
                .orElseThrow(() -> new IdempotencyKeyRequiredError("Falta la cabecera Idempotency-Key"));
        String scope = command.idempotencyScope();
        String signature = CommandSignature.of(command);

        // La repetición con la misma clave devuelve el pedido en su estado ACTUAL, sin ejecutar nada.
        Optional<IdempotencyStore.StoredRequest> previous = idempotencyStore.find(scope, key);
        if (previous.isPresent()) {
            if (!previous.get().signature().equals(signature)) {
                throw new IdempotencyReuseException(scope, key);
            }
            UUID existingId = UUID.fromString(previous.get().resourceId());
            Order existing = orderRepository.findById(existingId)
                    .orElseThrow(() -> new IdempotencyConflictException(scope, key, null));
            return orderApplicationMapper.toCreateOrderResponseDto(existing);
        }

        // Precondiciones, en el orden del diseño. Son lecturas sin efectos: ninguna deja nada escrito.
        boolean hasToken = command.paymentToken() != null && !command.paymentToken().isBlank();
        boolean hasMethod = command.paymentMethodId() != null;
        if (hasToken == hasMethod) {
            throw new PaymentSourceInvalidError("La petición necesita exactamente una fuente de pago: paymentToken o paymentMethodId");
        }
        Set<UUID> requested = new HashSet<>();
        for (OrderLineRequest line : command.lines()) {
            if (!requested.add(line.productId())) {
                throw new DuplicateOrderLineError("El producto " + line.productId() + " aparece en más de una línea");
            }
        }
        Map<UUID, Product> products = new LinkedHashMap<>();
        for (OrderLineRequest line : command.lines()) {
            Product product = productReader.byKey(line.productId());
            if (!product.isAvailable()) {
                throw new ProductNotAvailableError("El producto " + line.productId() + " está retirado");
            }
            products.put(line.productId(), product);
        }
        Set<String> currencies = new HashSet<>();
        products.values().forEach(product -> currencies.add(product.getCurrency()));
        if (currencies.size() != 1) {
            throw new CurrencyMismatchError("Los productos pedidos no comparten moneda");
        }
        String currency = currencies.iterator().next();

        // Reclama la clave ANTES de escribir el pedido: la carrera de dos peticiones iguales la arbitra la clave
        // primaria del registro (409 IDEMPOTENCY_KEY_IN_PROGRESS), no el negocio. El id del pedido se decide aquí.
        UUID orderId = Uuids.v7();
        idempotencyStore.save(scope, key, signature, orderId.toString(), IDEMPOTENCY_TTL_SECONDS);

        List<OrderLine> lines = new ArrayList<>();
        for (OrderLineRequest line : command.lines()) {
            Product product = products.get(line.productId());
            lines.add(OrderLine.create(product.getId(), product.getSku(), product.getName(), product.getPrice(), line.quantity()));
        }
        Order created = orderRepository.save(Order.create(orderId, command.customerRef(), email, currency, lines));

        // La llamada a payment va FUERA de toda transacción: el pedido ya está confirmado en pendingPayment.
        RequestChargeResult result;
        try {
            result = paymentClient.requestCharge(ChargeRequestIds.of(orderId), command.customerRef(), created.getTotalAmount(),
                    currency, hasToken ? command.paymentToken() : null, command.paymentMethodId());
        } catch (PaymentUnavailableException unavailable) {
            // Degrada: el pedido queda en pendingPayment; el desenlace llega por evento o lo resuelve el barrido.
            log.atWarn().addKeyValue("keel.order_id", orderId).log("payment no contesta: el pedido queda en pendingPayment");
            return orderApplicationMapper.toCreateOrderResponseDto(created);
        } catch (PaymentRejectedException rejected) {
            PaymentFailureReason reason = "PAYMENT_METHOD_UNAVAILABLE".equals(rejected.code())
                    ? PaymentFailureReason.INVALID_PAYMENT_METHOD
                    : PaymentFailureReason.PROCESSING_ERROR;
            return orderApplicationMapper.toCreateOrderResponseDto(
                    settle(orderId, order -> order.failPayment(null, reason)));
        }
        return orderApplicationMapper.toCreateOrderResponseDto(settle(orderId, order -> applyCharge(order, result)));
    }

    /**
     * Relee el pedido y, si sigue en pendingPayment, aplica el desenlace; si un evento de payment llegó antes y
     * ya no lo está, no lo toca. Un conflicto de versión con ese evento se resuelve releyendo.
     */
    private Order settle(UUID orderId, Consumer<Order> outcome) {
        for (int attempt = 1; ; attempt++) {
            Order current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("El pedido " + orderId + " recién creado no existe"));
            if (!current.isIn(OrderStatus.PENDING_PAYMENT)) {
                return current;
            }
            outcome.accept(current);
            try {
                return orderRepository.save(current);
            } catch (OptimisticLockingFailureException lost) {
                if (attempt >= SETTLE_ATTEMPTS) {
                    throw lost;
                }
            }
        }
    }

    /** El estado del cobro que informa payment, aplicado al pedido. La captura no la pide esta operación. */
    private static void applyCharge(Order order, RequestChargeResult result) {
        PaymentStatus status = result.status() != null ? result.status() : PaymentStatus.PENDING;
        UUID paymentId = result.id();
        switch (status) {
            case AUTHORIZED, CAPTURING, CAPTURED -> order.authorize(paymentId);
            case ACTION_REQUIRED -> {
                if (paymentId != null && result.customerAction() != null) {
                    order.requireCustomerAction(paymentId, result.customerAction());
                } else if (paymentId != null) {
                    order.registerPayment(paymentId);
                }
            }
            case FAILED -> order.failPayment(paymentId,
                    result.failureReason() != null ? result.failureReason() : PaymentFailureReason.PROCESSING_ERROR);
            case CANCELED -> order.markCanceled(paymentId);
            default -> {
                if (paymentId != null) {
                    order.registerPayment(paymentId);
                }
            }
        }
    }
}
