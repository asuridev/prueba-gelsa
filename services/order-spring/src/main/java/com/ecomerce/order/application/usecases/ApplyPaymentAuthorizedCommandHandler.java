package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentAuthorizedCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.support.AfterCommit;
import com.ecomerce.order.application.support.ChargeRequestIds;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.clients.PaymentClient;
import com.ecomerce.order.domain.clients.PaymentRejectedException;
import com.ecomerce.order.domain.clients.PaymentUnavailableException;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registra que payment autorizó el cobro del pedido y pide su captura.
 */
@ApplicationComponent
public class ApplyPaymentAuthorizedCommandHandler implements CommandHandler<ApplyPaymentAuthorizedCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentAuthorizedCommandHandler.class);

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final AfterCommit afterCommit;

    public ApplyPaymentAuthorizedCommandHandler(OrderRepository orderRepository, PaymentClient paymentClient, AfterCommit afterCommit) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.afterCommit = afterCommit;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentAuthorizedCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (order.isIn(OrderStatus.AUTHORIZED)) {
            order.renewCaptureAwaiting();
        } else if (order.isIn(OrderStatus.PENDING_PAYMENT, OrderStatus.ACTION_REQUIRED, OrderStatus.CANCELING)) {
            order.authorize(command.paymentId());
        } else {
            log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.order_status", order.getStatus().getValue())
                    .log("PaymentAuthorized sin efecto: el pedido ya está resuelto");
            return;
        }
        orderRepository.save(order);
        UUID paymentId = order.getPaymentId() != null ? order.getPaymentId() : command.paymentId();
        // La captura sale DESPUÉS de confirmar el estado: la llamada a payment nunca va dentro de la transacción.
        afterCommit.run(() -> requestCapture(order.getId(), paymentId));
    }

    /** La respuesta no mueve el pedido (lo hace PaymentCaptured o PaymentCanceled); si payment no contesta, el barrido reintenta. */
    private void requestCapture(UUID orderId, UUID paymentId) {
        try {
            paymentClient.capturePayment(paymentId);
        } catch (PaymentUnavailableException | PaymentRejectedException degraded) {
            log.atWarn().addKeyValue("keel.order_id", orderId)
                    .log("Captura sin respuesta de payment: el pedido sigue en authorized y el barrido la reintenta");
        }
    }
}
