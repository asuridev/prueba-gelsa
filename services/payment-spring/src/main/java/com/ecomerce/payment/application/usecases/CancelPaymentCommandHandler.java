package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.CancelPaymentCommand;
import com.ecomerce.payment.application.dtos.CancelPaymentResponseDto;
import com.ecomerce.payment.application.interfaces.ReturningCommandHandler;
import com.ecomerce.payment.application.mappers.PaymentApplicationMapper;
import com.ecomerce.payment.application.payment.PaymentVoiding;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.enums.PaymentStatus;
import com.ecomerce.payment.domain.errors.CancelRejectedError;
import com.ecomerce.payment.domain.errors.PaymentNotCancelableError;
import com.ecomerce.payment.domain.errors.PaymentNotFoundError;
import com.ecomerce.payment.domain.repository.PaymentRepository;

/**
 * Anula un cobro autorizado o que espera la autenticación del cliente, sin capturarlo.
 *
 * <p>Se despacha SIN transacción abarcadora: el paso a canceling se confirma ANTES de llamar a la pasarela, y el
 * desenlace (o la vuelta al origen si rechaza) se confirma después, también cuando la operación responde con error.
 */
@ApplicationComponent
public class CancelPaymentCommandHandler implements ReturningCommandHandler<CancelPaymentCommand, CancelPaymentResponseDto> {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final PaymentVoiding paymentVoiding;

    public CancelPaymentCommandHandler(PaymentRepository paymentRepository, PaymentApplicationMapper paymentApplicationMapper,
            PaymentVoiding paymentVoiding) {
        this.paymentRepository = paymentRepository;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.paymentVoiding = paymentVoiding;
    }

    @Override
    @LogExceptions
    public CancelPaymentResponseDto handle(CancelPaymentCommand command) {
        Payment payment = paymentRepository.findById(command.id())
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
        // La repetición se comprueba antes que cualquier otra guarda: no vuelve a llamar a la pasarela.
        if (payment.getStatus() == PaymentStatus.CANCELING || payment.getStatus() == PaymentStatus.CANCELED) {
            return paymentApplicationMapper.toCancelPaymentResponseDto(payment);
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED && payment.getStatus() != PaymentStatus.ACTION_REQUIRED) {
            throw new PaymentNotCancelableError("El cobro no está en authorized, actionRequired, canceling ni canceled");
        }
        // Paso a canceling, confirmado antes de llamar. Si otra petición lo cambió a la vez, el bloqueo optimista
        // lo traduce a CONCURRENT_MODIFICATION.
        payment.startCancel();
        payment = paymentRepository.save(payment);

        if (paymentVoiding.voidInFlight(payment) == PaymentVoiding.Result.REJECTED) {
            throw new CancelRejectedError("La pasarela rechazó la anulación");
        }
        // Sin respuesta o con desenlace aplicado (o ya movido por el aviso o el barrido): se relee y se responde como esté.
        return paymentRepository.findById(payment.getId())
                .map(paymentApplicationMapper::toCancelPaymentResponseDto)
                .orElseThrow(() -> new PaymentNotFoundError("No existe un cobro con ese id"));
    }
}
