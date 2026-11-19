package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.CapturePaymentCommand;
import com.ecomerce.payment.application.dtos.CapturePaymentResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.payment.PaymentOutcomeApplier;
import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.errors.AuthorizationExpiredError;
import com.ecomerce.payment.domain.errors.CaptureRejectedError;
import com.ecomerce.payment.domain.errors.PaymentNotCapturableError;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.repository.PaymentRepository;
import java.util.Set;

/**
 * Captura entero el importe de un cobro autorizado.
 *
 * <p>Se despacha SIN transacción abarcadora: el paso a capturing se confirma ANTES de llamar a la pasarela, y el
 * desenlace (o la vuelta a authorized si rechaza) se confirma después, también cuando la operación responde con error.
 */
@ApplicationComponent
public class CapturePaymentCommandHandler implements ReturningCommandHandler<CapturePaymentCommand, CapturePaymentResponseDto> {

    /** La repetición: la captura ya está pedida o hecha. */
    private static final Set<PaymentStatus> ALREADY_CAPTURED = Set.of(
            PaymentStatus.CAPTURING, PaymentStatus.CAPTURED, PaymentStatus.REFUNDING, PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeApplier outcomeApplier;

    public CapturePaymentCommandHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper,
            PaymentGateway paymentGateway, PaymentOutcomeApplier outcomeApplier) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.paymentGateway = paymentGateway;
        this.outcomeApplier = outcomeApplier;
    }

    @Override
    @LogExceptions
    public CapturePaymentResponseDto handle(CapturePaymentCommand command) {
        Payment payment = paymentRepository.findById(command.id())
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
        // La repetición se comprueba antes que cualquier otra guarda: no vuelve a llamar a la pasarela.
        if (ALREADY_CAPTURED.contains(payment.getStatus())) {
            return paymentApplicationMapper.toCapturePaymentResponseDto(payment);
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new PaymentNotCapturableError("El cobro no está en authorized, capturing ni captured");
        }
        // Paso a capturing, confirmado antes de llamar. Si otra petición lo cambió a la vez, el bloqueo optimista
        // lo traduce a CONCURRENT_MODIFICATION.
        payment.startCapture();
        payment = paymentRepository.save(payment);

        GatewayOutcome outcome;
        try {
            outcome = paymentGateway.capture(payment.getChargeRequestId(), payment.getGatewayPaymentId());
        } catch (PaymentGatewayUnavailableException noAnswer) {
            // No se sabe si capturó: no se repite; el cobro se queda en capturing y lo resuelve el barrido.
            return paymentApplicationMapper.toCapturePaymentResponseDto(payment);
        }
        outcome = new GatewayOutcome(outcome.status(), payment.getChargeRequestId(),
                outcome.gatewayPaymentId() != null ? outcome.gatewayPaymentId() : payment.getGatewayPaymentId(),
                outcome.failureReason(), outcome.customerAction(), outcome.refundedAmount());

        switch (outcome.status()) {
            case CAPTURED, REFUNDED -> outcomeApplier.apply(outcome);
            case PENDING -> {
                // Sin desenlace todavía: se queda en capturing.
            }
            case CANCELED -> {
                // La autorización ya había caducado: el cobro queda canceled (requested = false) y se responde el error.
                outcomeApplier.apply(outcome);
                throw new AuthorizationExpiredError("La pasarela ya había cancelado la autorización");
            }
            default -> {
                // Rechazo por otro motivo: vuelve a authorized y se publica PaymentActionRejected, aunque se responda con error.
                Payment current = paymentRepository.findById(payment.getId()).orElse(payment);
                if (current.captureRejected()) {
                    paymentRepository.save(current);
                    throw new CaptureRejectedError("La pasarela rechazó la captura");
                }
            }
        }
        // Si el aviso o el barrido movieron el cobro antes, se relee y se responde tal como esté, sin error.
        return paymentRepository.findById(payment.getId())
                .map(paymentApplicationMapper::toCapturePaymentResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
    }
}
