package com.ecomerce.payment.application.usecases;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.annotations.LogExceptions;
import com.ecomerce.payment.application.commands.SweepPendingPaymentsCommand;
import com.ecomerce.payment.application.interfaces.CommandHandler;
import com.ecomerce.payment.application.payment.PaymentReconciliation;
import com.ecomerce.payment.application.payment.PaymentVoiding;
import com.ecomerce.payment.application.support.SweepPass;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.parameters.PaymentParameters;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consulta a la pasarela los cobros que llevan más de 15 minutos esperando un desenlace sin respuesta y aplica lo que diga.
 *
 * <p>Se despacha SIN transacción abarcadora: el orden de commits es (1) reclamar cada candidato y confirmar, (2) llamar
 * a la pasarela fuera de toda transacción, (3) confirmar el desenlace. Cada cobro se procesa aislado: un fallo con uno
 * no aborta la pasada ni deshace los demás.
 */
@ApplicationComponent
public class SweepPendingPaymentsCommandHandler implements CommandHandler<SweepPendingPaymentsCommand> {

    private static final Logger log = LoggerFactory.getLogger(SweepPendingPaymentsCommandHandler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentReconciliation paymentReconciliation;
    private final PaymentVoiding paymentVoiding;
    private final PaymentParameters paymentParameters;

    public SweepPendingPaymentsCommandHandler(PaymentRepository paymentRepository, PaymentReconciliation paymentReconciliation,
            PaymentVoiding paymentVoiding, PaymentParameters paymentParameters) {
        this.paymentRepository = paymentRepository;
        this.paymentReconciliation = paymentReconciliation;
        this.paymentVoiding = paymentVoiding;
        this.paymentParameters = paymentParameters;
    }

    @Override
    @LogExceptions
    public void handle(SweepPendingPaymentsCommand command) {
        // Los que esperan desde antes del umbral, los más antiguos primero y en lote acotado. Sin límite de antigüedad:
        // tras una parada recupera todo lo pendiente.
        List<Payment> candidates = paymentRepository.findAwaitingBefore(
                paymentReconciliation.staleBefore(Instant.now()), paymentReconciliation.batchSize());
        // Un lote lleno puede dejar candidatos: el tick repite la pasada.
        SweepPass.claimed(candidates.size(), paymentReconciliation.batchSize());
        for (Payment candidate : candidates) {
            try {
                sweepOne(candidate);
            } catch (RuntimeException failure) {
                // Aislado: el cobro se queda como estaba y vuelve a consultarse cuando supere otra vez el umbral.
                log.atWarn()
                        .addKeyValue("keel.payment_id", candidate.getId())
                        .addKeyValue("keel.error_type", failure.getClass().getName())
                        .setCause(failure)
                        .log("Barrido: el cobro {} queda para otra pasada", candidate.getId());
            }
        }
    }

    private void sweepOne(Payment candidate) {
        // El reclamo: renueva awaitingSince con una actualización condicional que confirma ya; la réplica que pierde la
        // carrera ve 0 filas y salta el cobro. Sin él, N réplicas consultarían (y aplicarían) lo mismo N veces.
        if (!paymentRepository.claimAwaiting(candidate.getId(), candidate.getAwaitingSince(), Instant.now())) {
            return;
        }
        GatewayStatus gatewayStatus;
        try {
            gatewayStatus = paymentReconciliation.consult(candidate.getChargeRequestId(), candidate.getGatewayPaymentId());
        } catch (PaymentGatewayUnavailableException noAnswer) {
            // Sin respuesta no se da por fallido a ciegas: se queda como estaba, sin techo de intentos.
            return;
        }
        // Lo que dijo la pasarela puede haberlo aplicado ya el aplicador de desenlaces; se relee para decidir el resto.
        Payment current = paymentRepository.findById(candidate.getId()).orElse(null);
        if (current == null) {
            return;
        }
        switch (current.getStatus()) {
            // La acción en vuelo no llegó a hacerse (o fue rechazada): el cobro vuelve al estado del que salió.
            case CAPTURING -> {
                if (gatewayStatus == GatewayStatus.AUTHORIZED && current.captureRejected()) {
                    paymentRepository.save(current);
                }
            }
            case CANCELING -> {
                if ((gatewayStatus == GatewayStatus.AUTHORIZED || gatewayStatus == GatewayStatus.ACTION_REQUIRED) && current.cancelRejected()) {
                    paymentRepository.save(current);
                }
            }
            case REFUNDING -> {
                if (gatewayStatus == GatewayStatus.CAPTURED && current.refundRejected()) {
                    paymentRepository.save(current);
                }
            }
            // El plazo del 3DS cuenta con actionRequiredSince (no con awaitingSince, que el barrido renueva en cada consulta).
            case ACTION_REQUIRED -> {
                Instant limit = Instant.now().minus(Duration.ofHours(paymentParameters.actionRequiredTimeoutHours()));
                if (gatewayStatus == GatewayStatus.ACTION_REQUIRED && current.getActionRequiredSince().isBefore(limit)) {
                    // Se anula como en cancelPayment: canceling confirmado, y se pide la anulación a la pasarela.
                    current.startCancel();
                    paymentVoiding.voidInFlight(paymentRepository.save(current));
                }
            }
            default -> {
                // pending (resuelto por el aplicador, o sin desenlace aún) y los demás: nada más que hacer.
            }
        }
    }
}
