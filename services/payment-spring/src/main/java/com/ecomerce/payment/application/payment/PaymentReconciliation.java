package com.ecomerce.payment.application.payment;

import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.enums.FailureReason;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La consulta del barrido de reconciliación (sweepPendingPayments) para UN cobro que espera
 * desenlace: le pregunta a la pasarela y aplica lo que diga.
 *
 * <p>Lo que no hace es elegir los candidatos ni reclamarlos: eso es del handler del barrido. Los
 * candidatos son los cobros en pending, actionRequired, capturing, canceling, refunding cuyo awaitingSince es
 * anterior a {@link #staleBefore}, en lotes de {@link #batchSize}; y el handler reclama cada uno
 * volviendo a estampar awaitingSince ANTES de llamar aquí, para que otra réplica no lo
 * consulte en la misma pasada.
 *
 * <p>El barrido lo dispara el cronómetro en una réplica por tick, y el tick DRENA: si el handler reclamó un lote
 * lleno ({@link #batchSize} cobros), que lo avise con {@code SweepPass.claimed(reclamados, batchSize())}
 * (application/support) y el tick repetirá la pasada.
 */
@Component
public class PaymentReconciliation {

    private final PaymentGateway gateway;
    private final PaymentOutcomeApplier applier;
    private final long unansweredAfterSeconds;
    private final int batchSize;

    public PaymentReconciliation(PaymentGateway gateway, PaymentOutcomeApplier applier,
            @Value("${payments.reconciliation.unanswered-after-seconds}") long unansweredAfterSeconds,
            @Value("${payments.reconciliation.batch-size:50}") int batchSize) {
        if (unansweredAfterSeconds < 1 || batchSize < 1) {
            throw new IllegalArgumentException("El umbral y el lote del barrido de pagos tienen que ser positivos");
        }
        this.gateway = gateway;
        this.applier = applier;
        this.unansweredAfterSeconds = unansweredAfterSeconds;
        this.batchSize = batchSize;
    }

    /** El corte del barrido: un cobro que espera desde antes de este instante lleva demasiado sin desenlace. */
    public Instant staleBefore(Instant now) {
        return now.minusSeconds(unansweredAfterSeconds);
    }

    /**
     * Cuántos cobros consulta una pasada como máximo ({@code payments.reconciliation.batch-size}): capacidad, no
     * diseño. Sin cota, una tanda con miles de atascados son miles de llamadas a la pasarela en una sola pasada.
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * @return el estado que dio la pasarela. Con PENDING no se aplica nada: el cobro se queda para la
     *         siguiente pasada. Con el estado del que salió una acción de seguimiento (AUTHORIZED tras
     *         capturing o canceling, CAPTURED tras refunding), la acción no llegó a hacerse y el
     *         handler devuelve el cobro a ese estado.
     */
    public GatewayStatus consult(String reference, String gatewayPaymentId) {
        GatewayOutcome outcome = gateway.status(reference, gatewayPaymentId);
        if (outcome.status() == GatewayStatus.NOT_FOUND) {
            // La petición no llegó nunca a la pasarela: el cobro falló sin que nadie cobrara nada.
            applier.apply(GatewayOutcome.failed(reference, null, FailureReason.NOT_RECEIVED));
            return GatewayStatus.NOT_FOUND;
        }
        if (outcome.status() != GatewayStatus.PENDING) {
            applier.apply(outcome);
        }
        return outcome.status();
    }
}
