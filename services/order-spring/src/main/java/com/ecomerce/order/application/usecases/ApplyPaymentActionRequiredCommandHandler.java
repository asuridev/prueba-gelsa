package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentActionRequiredCommand;
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
 * Registra que el cobro del pedido necesita que el comprador complete el 3DS.
 */
@ApplicationComponent
public class ApplyPaymentActionRequiredCommandHandler implements CommandHandler<ApplyPaymentActionRequiredCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentActionRequiredCommandHandler.class);

    private final OrderRepository orderRepository;

    public ApplyPaymentActionRequiredCommandHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentActionRequiredCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (!order.isIn(OrderStatus.PENDING_PAYMENT)) {
            log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.order_status", order.getStatus().getValue())
                    .log("PaymentActionRequired sin efecto: el pedido ya no está en pendingPayment");
            return;
        }
        order.requireCustomerAction(command.paymentId(), command.customerAction());
        orderRepository.save(order);
    }
}
