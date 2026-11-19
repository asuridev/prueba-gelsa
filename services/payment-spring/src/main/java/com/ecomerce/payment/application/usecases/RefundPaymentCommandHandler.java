package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.RefundPaymentCommand;
import com.ecomerce.payment.application.dtos.RefundPaymentResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.payment.PaymentOutcomeApplier;
import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.entity.Refund;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.errors.RefundRejectedError;
import com.ecomerce.payment.domain.errors.RefundRequestConflictError;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Devuelve todo o parte de lo que queda por devolver de un cobro capturado.
 *
 * <p>Se despacha SIN transacción abarcadora: el cobro pasa a refunding (con la devolución en pending) y se confirma
 * ANTES de llamar a la pasarela; el desenlace se confirma después, también cuando la operación responde con error.
 */
@ApplicationComponent
public class RefundPaymentCommandHandler implements ReturningCommandHandler<RefundPaymentCommand, RefundPaymentResponseDto> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeApplier outcomeApplier;

    public RefundPaymentCommandHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper,
            PaymentGateway paymentGateway, PaymentOutcomeApplier outcomeApplier) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.paymentGateway = paymentGateway;
        this.outcomeApplier = outcomeApplier;
    }

    @Override
    @LogExceptions
    public RefundPaymentResponseDto handle(RefundPaymentCommand command) {
        Payment payment = paymentRepository.findById(command.id())
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
        // La clave se comprueba justo después de que el cobro exista y antes que cualquier otra guarda. La que existe
        // sobre OTRO cobro la rechaza la clave natural (refundRequestId) al guardar: REFUND_REQUEST_CONFLICT.
        Refund existing = payment.findRefund(command.refundRequestId()).orElse(null);
        if (existing != null) {
            if (command.amount() == null || command.amount().compareTo(existing.getAmount()) == 0) {
                return paymentApplicationMapper.toRefundPaymentResponseDto(payment);
            }
            throw new RefundRequestConflictError("Ya existe una devolución con ese refundRequestId con otro importe");
        }
        // Estado, decimales según la moneda y saldo (en ese orden), y paso a refunding con la devolución en pending.
        payment.startRefund(command.refundRequestId(), command.amount(), command.reason());
        payment = paymentRepository.save(payment);
        Refund pending = payment.findRefund(command.refundRequestId()).orElseThrow();

        GatewayOutcome outcome;
        try {
            // La moneda es la del cobro: ya la tenemos, no se le pregunta a la pasarela.
            outcome = paymentGateway.refund(payment.getChargeRequestId(), payment.getGatewayPaymentId(), pending.getAmount(), payment.getCurrency());
        } catch (PaymentGatewayUnavailableException noAnswer) {
            // No se sabe si devolvió: no se repite; el cobro se queda en refunding y lo resuelve el barrido.
            return paymentApplicationMapper.toRefundPaymentResponseDto(payment);
        }
        outcome = new GatewayOutcome(outcome.status(), payment.getChargeRequestId(),
                outcome.gatewayPaymentId() != null ? outcome.gatewayPaymentId() : payment.getGatewayPaymentId(),
                outcome.failureReason(), outcome.customerAction(), outcome.refundedAmount());

        if (outcome.status() == GatewayStatus.REFUNDED) {
            outcomeApplier.apply(outcome);
        } else if (outcome.status() != GatewayStatus.PENDING) {
            // Cualquier otro resultado (la devolución vuelve como CAPTURED): la pasarela la rechazó. La devolución pasa
            // a failed, el cobro vuelve a captured y se publica PaymentActionRejected, aunque se responda con error.
            Payment current = paymentRepository.findById(payment.getId()).orElse(payment);
            if (current.refundRejected()) {
                paymentRepository.save(current);
                throw new RefundRejectedError("La pasarela rechazó la devolución");
            }
        }
        // Si el aviso o el barrido movieron el cobro antes, se relee y se responde tal como esté, sin error.
        return paymentRepository.findById(payment.getId())
                .map(paymentApplicationMapper::toRefundPaymentResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
    }
}
