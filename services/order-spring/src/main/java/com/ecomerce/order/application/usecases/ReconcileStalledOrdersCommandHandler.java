package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentActionRequiredCommand;
import com.ecomerce.order.application.commands.ApplyPaymentAuthorizedCommand;
import com.ecomerce.order.application.commands.ApplyPaymentCanceledCommand;
import com.ecomerce.order.application.commands.ApplyPaymentCapturedCommand;
import com.ecomerce.order.application.commands.ApplyPaymentFailedCommand;
import com.ecomerce.order.application.commands.ReconcileStalledOrdersCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.port.out.CommandDispatcher;
import com.ecomerce.order.application.support.ChargeRequestIds;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.clients.CancelPaymentResult;
import com.ecomerce.order.domain.clients.CapturePaymentResult;
import com.ecomerce.order.domain.clients.GetPaymentByChargeRequestResult;
import com.ecomerce.order.domain.clients.PaymentClient;
import com.ecomerce.order.domain.clients.PaymentRejectedException;
import com.ecomerce.order.domain.clients.PaymentUnavailableException;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.errors.DomainException;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import com.ecomerce.order.domain.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Rescata los pedidos que se quedaron esperando un desenlace de payment - los que nunca llegaron a registrarse como cobro, los autorizados cuya captura no se confirmó y los que esperan una anulación.
 *
 * <p>Corre SIN transacción abarcadora: el reclamo de cada lote ya está confirmado cuando el adaptador lo
 * devuelve, las llamadas a payment van fuera de toda transacción y cada despacho abre y confirma la suya. Un
 * pedido que falla no corta el ciclo.
 */
@ApplicationComponent
public class ReconcileStalledOrdersCommandHandler implements CommandHandler<ReconcileStalledOrdersCommand> {

    private static final Logger log = LoggerFactory.getLogger(ReconcileStalledOrdersCommandHandler.class);

    private static final int WRITE_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final CommandDispatcher commandDispatcher;

    public ReconcileStalledOrdersCommandHandler(OrderRepository orderRepository, PaymentClient paymentClient, CommandDispatcher commandDispatcher) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    @LogExceptions
    public void handle(ReconcileStalledOrdersCommand command) {
        List<Order> charges = orderRepository.claimForReconcileStalledOrdersChargeOrder();
        sweep(charges, "chargeOrder", order -> {
            if (order.isIn(OrderStatus.PENDING_PAYMENT)) {
                reconcileCharge(order);
            } else if (order.isIn(OrderStatus.ACTION_REQUIRED)) {
                reconcileActionRequired(order);
            }
        });
        List<Order> captures = orderRepository.claimForReconcileStalledOrdersCaptureOrderPayment();
        sweep(captures, "captureOrderPayment", this::retryCapture);
        List<Order> cancellations = orderRepository.claimForReconcileStalledOrdersCancelOrderPayment();
        sweep(cancellations, "cancelOrderPayment", this::retryCancel);
    }

    /** Un pedido que falla se registra y queda para el siguiente ciclo; no corta el lote. */
    private void sweep(List<Order> claimed, String activation, Consumer<Order> action) {
        if (claimed.isEmpty()) {
            return;
        }
        int processed = 0;
        for (Order order : claimed) {
            try {
                action.accept(order);
                processed++;
            } catch (DomainException | DataAccessException failure) {
                log.atWarn().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.activation", activation)
                        .addKeyValue("keel.error_type", failure.getClass().getSimpleName())
                        .log("Barrido: el pedido falló y queda para el siguiente ciclo");
            }
        }
        log.atInfo().addKeyValue("keel.activation", activation).addKeyValue("keel.claimed", claimed.size())
                .addKeyValue("keel.processed", processed).log("Barrido de {}: lote procesado", activation);
    }

    // ── pendingPayment: ¿registró payment el cobro? ─────────────────────────────────────────────

    private void reconcileCharge(Order order) {
        String chargeRequestId = ChargeRequestIds.of(order.getId());
        GetPaymentByChargeRequestResult payment;
        try {
            payment = paymentClient.getPaymentByChargeRequest(chargeRequestId);
        } catch (PaymentRejectedException rejected) {
            if (rejected.status() == 404) {
                // payment nunca lo registró: el cobro no llegó.
                mutate(order.getId(), OrderStatus.PENDING_PAYMENT, pending -> pending.failPayment(null, PaymentFailureReason.NOT_RECEIVED));
            } else {
                log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment rechazó la consulta del cobro: se reintenta en el siguiente ciclo");
            }
            return;
        } catch (PaymentUnavailableException unavailable) {
            log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment no contesta: el pedido sigue igual");
            return;
        }
        dispatchOutcome(order, chargeRequestId, payment, OrderStatus.PENDING_PAYMENT);
    }

    // ── actionRequired: el desenlace del 3DS no llegó ──────────────────────────────────────────

    private void reconcileActionRequired(Order order) {
        String chargeRequestId = ChargeRequestIds.of(order.getId());
        GetPaymentByChargeRequestResult payment;
        try {
            payment = paymentClient.getPaymentByChargeRequest(chargeRequestId);
        } catch (PaymentRejectedException | PaymentUnavailableException failure) {
            log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment no resolvió la consulta del cobro: se reintenta en el siguiente ciclo");
            return;
        }
        dispatchOutcome(order, chargeRequestId, payment, OrderStatus.ACTION_REQUIRED);
    }

    /** Despacha la operación apply del estado en que payment tiene el cobro. */
    private void dispatchOutcome(Order order, String chargeRequestId, GetPaymentByChargeRequestResult payment, OrderStatus waitingIn) {
        PaymentStatus status = payment.status() != null ? payment.status() : PaymentStatus.PENDING;
        UUID paymentId = payment.id();
        switch (status) {
            // La autorización pide la captura (I/O externo): sin transacción abarcadora.
            case AUTHORIZED, CAPTURING -> commandDispatcher.dispatchWithoutTransaction(new ApplyPaymentAuthorizedCommand(chargeRequestId, paymentId));
            case CAPTURED -> {
                commandDispatcher.dispatchWithoutTransaction(new ApplyPaymentAuthorizedCommand(chargeRequestId, paymentId));
                commandDispatcher.dispatch(new ApplyPaymentCapturedCommand(chargeRequestId));
            }
            case ACTION_REQUIRED -> {
                if (waitingIn == OrderStatus.PENDING_PAYMENT) {
                    if (payment.customerAction() == null) {
                        log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment informa actionRequired sin customerAction: se reintenta");
                    } else {
                        commandDispatcher.dispatch(new ApplyPaymentActionRequiredCommand(chargeRequestId, paymentId, payment.customerAction()));
                    }
                }
                // En actionRequired el pedido sigue esperando el 3DS: no hace nada.
            }
            case FAILED -> commandDispatcher.dispatch(new ApplyPaymentFailedCommand(chargeRequestId, paymentId,
                    payment.failureReason() != null ? payment.failureReason() : PaymentFailureReason.PROCESSING_ERROR));
            case CANCELED -> commandDispatcher.dispatch(new ApplyPaymentCanceledCommand(chargeRequestId, paymentId));
            case PENDING -> {
                if (waitingIn == OrderStatus.PENDING_PAYMENT) {
                    // Renueva el reloj para no volver a consultarlo hasta pasado el plazo.
                    mutate(order.getId(), OrderStatus.PENDING_PAYMENT, Order::renewChargeAwaiting);
                }
            }
            default -> log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.payment_status", status.getValue())
                    .log("Barrido: el estado del cobro no mueve el pedido");
        }
    }

    // ── authorized: la captura no se confirmó ──────────────────────────────────────────────────

    private void retryCapture(Order order) {
        if (!order.isIn(OrderStatus.AUTHORIZED) || order.getPaymentId() == null) {
            return;
        }
        String chargeRequestId = ChargeRequestIds.of(order.getId());
        CapturePaymentResult result;
        try {
            result = paymentClient.capturePayment(order.getPaymentId());
        } catch (PaymentRejectedException | PaymentUnavailableException failure) {
            log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment no resolvió la captura: se reintenta en el siguiente ciclo");
            return;
        }
        PaymentStatus status = result.status() != null ? result.status() : PaymentStatus.PENDING;
        switch (status) {
            case CAPTURED -> commandDispatcher.dispatch(new ApplyPaymentCapturedCommand(chargeRequestId));
            case CANCELED -> commandDispatcher.dispatch(new ApplyPaymentCanceledCommand(chargeRequestId, order.getPaymentId()));
            default -> mutate(order.getId(), OrderStatus.AUTHORIZED, Order::renewCaptureAwaiting);
        }
    }

    // ── canceling: la anulación no se confirmó ─────────────────────────────────────────────────

    private void retryCancel(Order order) {
        if (!order.isIn(OrderStatus.CANCELING) || order.getPaymentId() == null) {
            return;
        }
        String chargeRequestId = ChargeRequestIds.of(order.getId());
        CancelPaymentResult result;
        try {
            result = paymentClient.cancelPayment(order.getPaymentId());
        } catch (PaymentRejectedException | PaymentUnavailableException failure) {
            log.atWarn().addKeyValue("keel.order_id", order.getId()).log("payment no resolvió la anulación: se reintenta en el siguiente ciclo");
            return;
        }
        PaymentStatus status = result.status() != null ? result.status() : PaymentStatus.CANCELING;
        switch (status) {
            case CANCELED -> commandDispatcher.dispatch(new ApplyPaymentCanceledCommand(chargeRequestId, order.getPaymentId()));
            case FAILED -> commandDispatcher.dispatch(new ApplyPaymentFailedCommand(chargeRequestId, order.getPaymentId(),
                    result.failureReason() != null ? result.failureReason() : PaymentFailureReason.PROCESSING_ERROR));
            case AUTHORIZED, CAPTURING, CAPTURED -> commandDispatcher.dispatchWithoutTransaction(
                    new ApplyPaymentAuthorizedCommand(chargeRequestId, order.getPaymentId()));
            default -> mutate(order.getId(), OrderStatus.CANCELING, Order::renewCancelAwaiting);
        }
    }

    /**
     * Relee el pedido y aplica el cambio solo si sigue en el estado de espera; si ya salió de él, ganó el otro
     * camino (la carrera resuelta, no un fallo). Un conflicto de versión se resuelve releyendo.
     */
    private void mutate(UUID orderId, OrderStatus expected, Consumer<Order> change) {
        for (int attempt = 1; ; attempt++) {
            Order current = orderRepository.findById(orderId).orElse(null);
            if (current == null || !current.isIn(expected)) {
                return;
            }
            change.accept(current);
            try {
                orderRepository.save(current);
                return;
            } catch (OptimisticLockingFailureException lost) {
                if (attempt >= WRITE_ATTEMPTS) {
                    throw lost;
                }
            }
        }
    }
}
