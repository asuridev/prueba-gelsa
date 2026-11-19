package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.commands.ApplyPaymentActionRejectedCommand;
import com.ecomerce.order.application.interfaces.CommandHandler;
import com.ecomerce.order.application.support.ChargeRequestIds;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.enums.ApplyPaymentActionRejectedAction;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Devuelve el pedido al estado que informa payment cuando la pasarela rechaza la anulación de su cobro.
 */
@ApplicationComponent
public class ApplyPaymentActionRejectedCommandHandler implements CommandHandler<ApplyPaymentActionRejectedCommand> {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentActionRejectedCommandHandler.class);

    private final OrderRepository orderRepository;

    public ApplyPaymentActionRejectedCommandHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @LogExceptions
    public void handle(ApplyPaymentActionRejectedCommand command) {
        Optional<UUID> orderId = ChargeRequestIds.orderIdOf(command.chargeRequestId());
        if (orderId.isEmpty()) {
            return;
        }
        Optional<Order> found = orderRepository.findById(orderId.get());
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (command.action() == ApplyPaymentActionRejectedAction.CAPTURE) {
            // El pedido sigue en authorized; la captura la reintenta reconcileStalledOrders.
            log.atDebug().addKeyValue("keel.order_id", order.getId()).log("Captura rechazada por la pasarela: el barrido la reintenta");
            return;
        }
        if (command.action() != ApplyPaymentActionRejectedAction.CANCEL || !order.isIn(OrderStatus.CANCELING)) {
            return;
        }
        switch (command.status()) {
            case ACTION_REQUIRED -> order.resumeCustomerAction();
            case AUTHORIZED, CAPTURING, CAPTURED -> order.authorize(null);
            default -> {
                log.atInfo().addKeyValue("keel.order_id", order.getId()).addKeyValue("keel.payment_status", command.status().getValue())
                        .log("PaymentActionRejected sin efecto: el estado del cobro no reabre el pedido");
                return;
            }
        }
        orderRepository.save(order);
    }
}
