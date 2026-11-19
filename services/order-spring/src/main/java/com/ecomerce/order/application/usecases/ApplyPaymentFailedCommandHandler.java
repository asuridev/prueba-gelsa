package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentFailedCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.support.ChargeRequestIds;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marca el pedido con pago fallido cuando payment informa el fallo del cobro y avisa al comprador.
 */
@ApplicationComponent
public class ApplyPaymentFailedCommandHandler implements CommandHandler<ApplyPaymentFailedCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentFailedCommandHandler.class);

    private final OrderRepository orderRepository;

    public ApplyPaymentFailedCommandHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentFailedCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (!order.isIn(OrderStatus.PENDING_PAYMENT, OrderStatus.ACTION_REQUIRED, OrderStatus.CANCELING)) {
            log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.order_status", order.getStatus().getValue())
                    .log("PaymentFailed sin efecto: el pedido ya salió de la espera del cobro");
            return;
        }
        order.failPayment(command.paymentId(), command.failureReason());
        orderRepository.save(order);
    }
}
