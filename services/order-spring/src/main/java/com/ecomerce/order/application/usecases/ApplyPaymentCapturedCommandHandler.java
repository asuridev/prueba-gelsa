package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentCapturedCommand;
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
 * Marca el pedido como pagado cuando payment confirma la captura y avisa al comprador.
 */
@ApplicationComponent
public class ApplyPaymentCapturedCommandHandler implements CommandHandler<ApplyPaymentCapturedCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentCapturedCommandHandler.class);

    private final OrderRepository orderRepository;

    public ApplyPaymentCapturedCommandHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentCapturedCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (!order.isIn(OrderStatus.AUTHORIZED)) {
            log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.order_status", order.getStatus().getValue())
                    .log("PaymentCaptured sin efecto: el pedido no está en authorized");
            return;
        }
        order.markPaid();
        orderRepository.save(order);
    }
}
