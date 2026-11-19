package com.ecomerce.payment.application.payment;

import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.repository.PaymentRepository;
import org.springframework.stereotype.Component;

/**
 * La anulación de un cobro que YA está en canceling (confirmado): llama a la pasarela y aplica lo que conteste.
 * La comparten cancelPayment y el plazo del 3DS de sweepPendingPayments, que la piden igual («se anula como en
 * cancelPayment»).
 */
@Component
public class PaymentVoiding {

    /** Qué pasó con la anulación. */
    public enum Result {
        /** La pasarela contestó y el desenlace está aplicado (canceled, o failed si el cliente falló la autenticación). */
        DONE,
        /** La pasarela no contestó: el cobro sigue en canceling y lo resuelve el barrido. */
        UNANSWERED,
        /** La pasarela rechazó: el cobro volvió al estado del que salió, con el rechazo confirmado y publicado. */
        REJECTED
    }

    private final PaymentGateway gateway;
    private final PaymentOutcomeApplier applier;
    private final PaymentRepository paymentRepository;

    public PaymentVoiding(PaymentGateway gateway, PaymentOutcomeApplier applier, PaymentRepository paymentRepository) {
        this.gateway = gateway;
        this.applier = applier;
        this.paymentRepository = paymentRepository;
    }

    public Result voidInFlight(Payment canceling) {
        GatewayOutcome outcome;
        try {
            outcome = gateway.voidAuthorization(canceling.getChargeRequestId(), canceling.getGatewayPaymentId());
        } catch (PaymentGatewayUnavailableException noAnswer) {
            // No se sabe qué hizo: no se reintenta, lo resuelve el barrido preguntando.
            return Result.UNANSWERED;
        }
        outcome = GatewayOutcomes.forPayment(outcome, canceling.getChargeRequestId(), canceling.getGatewayPaymentId());
        switch (outcome.status()) {
            case CANCELED, FAILED -> {
                applier.apply(outcome);
                return Result.DONE;
            }
            case PENDING -> {
                return Result.UNANSWERED;
            }
            default -> {
                // La pasarela no anuló. Se relee: si el aviso o el barrido ya movieron el cobro, se deja como esté.
                Payment current = paymentRepository.findById(canceling.getId()).orElse(null);
                if (current != null && current.getStatus() == PaymentStatus.CANCELING && current.cancelRejected()) {
                    paymentRepository.save(current);
                    return Result.REJECTED;
                }
                return Result.DONE;
            }
        }
    }
}
