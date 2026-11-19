package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.CancelOrderCommand;
import com.ecomerce.order.application.dtos.CancelOrderResponseDto;
import com.ecomerce.order.application.interfaces.ReturningCommandHandler;
import com.ecomerce.order.application.mappers.OrderApplicationMapper;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.clients.CancelPaymentResult;
import com.ecomerce.order.domain.clients.PaymentClient;
import com.ecomerce.order.domain.clients.PaymentRejectedException;
import com.ecomerce.order.domain.clients.PaymentUnavailableException;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import com.ecomerce.order.domain.errors.OrderCancelRejectedError;
import com.ecomerce.order.domain.errors.OrderNotCancelableError;
import com.ecomerce.order.domain.errors.OrderNotFoundError;
import com.ecomerce.order.domain.errors.OrderNotOwnedError;
import com.ecomerce.order.domain.errors.OrderPaymentAdvancedError;
import com.ecomerce.order.domain.repository.OrderRepository;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Anula un pedido que espera la autenticación del comprador (3DS) pidiendo a payment que anule su cobro; el pedido queda en canceling hasta que payment confirma el desenlace.
 *
 * <p>Corre SIN transacción abarcadora (el controller despacha con dispatchWithoutTransaction): canceling se
 * confirma ANTES de llamar a cancelPayment, y la respuesta se aplica releyendo el pedido.
 */
@ApplicationComponent
public class CancelOrderCommandHandler implements ReturningCommandHandler<CancelOrderCommand, CancelOrderResponseDto> {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderCommandHandler.class);

    /** Intentos de aplicar el desenlace cuando un evento de payment escribe el pedido a la vez. */
    private static final int SETTLE_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final OrderApplicationMapper orderApplicationMapper;
    private final PaymentClient paymentClient;

    public CancelOrderCommandHandler(OrderRepository orderRepository, OrderApplicationMapper orderApplicationMapper, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.orderApplicationMapper = orderApplicationMapper;
        this.paymentClient = paymentClient;
    }

    @Override
    @LogExceptions
    public CancelOrderResponseDto handle(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.id())
                .orElseThrow(() -> new OrderNotFoundError("No existe el pedido " + command.id()));
        if (!order.getCustomerRef().equals(command.customerRef())) {
            throw new OrderNotOwnedError("El pedido " + command.id() + " es de otro comprador");
        }
        if (order.isIn(OrderStatus.CANCELING, OrderStatus.CANCELED)) {
            // Idempotente por estado: se devuelve tal cual sin volver a llamar a payment.
            return orderApplicationMapper.toCancelOrderResponseDto(order);
        }
        if (!order.isIn(OrderStatus.ACTION_REQUIRED)) {
            throw new OrderNotCancelableError("El pedido " + command.id() + " no está esperando la autenticación del comprador");
        }

        // canceling se confirma ANTES de llamar a payment.
        order.startCancellation();
        Order canceling = orderRepository.save(order);

        CancelPaymentResult result;
        try {
            result = paymentClient.cancelPayment(canceling.getPaymentId());
        } catch (PaymentUnavailableException unavailable) {
            // Degrada: el pedido queda en canceling y el barrido vuelve a pedir la anulación.
            log.atWarn().addKeyValue("keel.order_id", canceling.getId()).log("payment no contesta: el pedido queda en canceling");
            return orderApplicationMapper.toCancelOrderResponseDto(canceling);
        } catch (PaymentRejectedException rejected) {
            String code = rejected.code();
            if ("PAYMENT_NOT_CANCELABLE".equals(code)) {
                settle(canceling.getId(), Order::resumeCustomerAction);
                throw new OrderPaymentAdvancedError("payment ya no admite anular el cobro: avanzó");
            }
            if ("CANCEL_REJECTED".equals(code)) {
                settle(canceling.getId(), Order::resumeCustomerAction);
                throw new OrderCancelRejectedError("La pasarela rechazó la anulación");
            }
            // PAYMENT_NOT_FOUND u otro rechazo sin significado propio: se trata como si no contestara.
            log.atWarn().addKeyValue("keel.order_id", canceling.getId()).addKeyValue("keel.payment_error", code)
                    .log("payment rechazó la anulación sin desenlace: el pedido queda en canceling");
            return orderApplicationMapper.toCancelOrderResponseDto(canceling);
        }

        PaymentStatus status = result.status() != null ? result.status() : PaymentStatus.CANCELING;
        UUID paymentId = result.id();
        boolean[] rejectedByGateway = new boolean[1];
        Order current = settle(canceling.getId(), pending -> {
            rejectedByGateway[0] = false;
            switch (status) {
                case CANCELED -> pending.markCanceled(paymentId);
                case FAILED -> pending.failPayment(paymentId,
                        result.failureReason() != null ? result.failureReason() : PaymentFailureReason.PROCESSING_ERROR);
                case AUTHORIZED, CAPTURING, CAPTURED -> pending.authorize(paymentId);
                case ACTION_REQUIRED -> {
                    pending.resumeCustomerAction();
                    rejectedByGateway[0] = true;
                }
                default -> {
                    // canceling: el pedido sigue en canceling hasta que llegue el desenlace.
                }
            }
        });
        if (rejectedByGateway[0]) {
            throw new OrderCancelRejectedError("La pasarela rechazó la anulación");
        }
        return orderApplicationMapper.toCancelOrderResponseDto(current);
    }

    /**
     * Relee el pedido y, si sigue en canceling, aplica el desenlace; si un evento llegó antes y ya no lo está,
     * lo devuelve tal cual sin error. Un conflicto de versión con ese evento se resuelve releyendo.
     */
    private Order settle(UUID orderId, Consumer<Order> outcome) {
        for (int attempt = 1; ; attempt++) {
            Order current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundError("No existe el pedido " + orderId));
            if (!current.isIn(OrderStatus.CANCELING)) {
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
}
