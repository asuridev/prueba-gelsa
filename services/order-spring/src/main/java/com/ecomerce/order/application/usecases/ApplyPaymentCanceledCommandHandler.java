package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentCanceledCommand;
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
 * Marca el pedido como cancelado cuando payment confirma que la autorización quedó anulada y avisa al comprador.
 */
@ApplicationComponent
public class ApplyPaymentCanceledCommandHandler implements CommandHandler<ApplyPaymentCanceledCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentCanceledCommandHandler.class);

    private final OrderRepository orderRepository;

    public ApplyPaymentCanceledCommandHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentCanceledCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (!order.isIn(OrderStatus.PENDING_PAYMENT, OrderStatus.ACTION_REQUIRED, OrderStatus.AUTHORIZED, OrderStatus.CANCELING)) {
            log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.order_status", order.getStatus().getValue())
                    .log("PaymentCanceled sin efecto: el pedido ya está resuelto");
            return;
        }
        order.markCanceled(command.paymentId());
        orderRepository.save(order);
    }
}
