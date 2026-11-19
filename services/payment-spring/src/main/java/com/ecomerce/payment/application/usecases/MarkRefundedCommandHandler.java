package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.MarkRefundedCommand;
import com.ecomerce.payment.application.interfaces.CommandHandler;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Registra que la pasarela completó la devolución en curso del cobro.
 */
@ApplicationComponent
public class MarkRefundedCommandHandler implements CommandHandler<MarkRefundedCommand> {

    private final PaymentRepository paymentRepository;

    public MarkRefundedCommandHandler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @LogExceptions
    public void handle(MarkRefundedCommand command) {
        Payment payment = paymentRepository.findByChargeRequestId(command.chargeRequestId())
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese chargeRequestId"));
        // Idempotente: si el cobro ya no está en un estado de origen (otro camino lo aplicó, o llega tarde), no hace
        // nada y no es un error. El agregado lo decide y emite su evento; el save lo publica.
        if (payment.markRefunded(command.amount())) {
            paymentRepository.save(payment);
        }
    }
}
