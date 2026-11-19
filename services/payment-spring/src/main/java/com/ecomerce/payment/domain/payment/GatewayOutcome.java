package com.ecomerce.payment.domain.payment;

import com.ecomerce.payment.domain.enums.FailureReason;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo que la pasarela dice de un cobro, ya traducido. Es la ÚNICA forma en que un desenlace entra
 * en el servicio, venga de la respuesta síncrona, del aviso o del barrido: el aviso no se lee,
 * se consulta.
 *
 * @param status           el estado neutro
 * @param reference        la referencia de negocio del cobro, que el adaptador manda a la pasarela
 *                         y la pasarela devuelve; es lo que identifica el cobro en este servicio
 * @param gatewayPaymentId el id que asignó la pasarela, o null si no llegó a asignarlo
 * @param failureReason    con FAILED, el motivo en el vocabulario neutro; null en otro caso
 * @param customerAction   con ACTION_REQUIRED, la acción del cliente, opaca (la consume el
 *                         componente de la pasarela en el navegador); null en otro caso
 * @param refundedAmount   con REFUNDED, lo devuelto en unidades mayores; null en otro caso
 */
public record GatewayOutcome(GatewayStatus status, String reference, String gatewayPaymentId,
        FailureReason failureReason, String customerAction, BigDecimal refundedAmount) {

    public GatewayOutcome {
        Objects.requireNonNull(status, "status");
    }

    public static GatewayOutcome of(GatewayStatus status, String reference, String gatewayPaymentId) {
        return new GatewayOutcome(status, reference, gatewayPaymentId, null, null, null);
    }

    public static GatewayOutcome failed(String reference, String gatewayPaymentId, FailureReason reason) {
        return new GatewayOutcome(GatewayStatus.FAILED, reference, gatewayPaymentId, reason, null, null);
    }

    public static GatewayOutcome actionRequired(String reference, String gatewayPaymentId, String customerAction) {
        return new GatewayOutcome(GatewayStatus.ACTION_REQUIRED, reference, gatewayPaymentId, null, customerAction, null);
    }

    public static GatewayOutcome refunded(String reference, String gatewayPaymentId, BigDecimal refundedAmount) {
        return new GatewayOutcome(GatewayStatus.REFUNDED, reference, gatewayPaymentId, null, null, refundedAmount);
    }

    /** La pasarela no conoce el cobro: la petición no le llegó nunca. */
    public static GatewayOutcome notFound(String reference) {
        return new GatewayOutcome(GatewayStatus.NOT_FOUND, reference, null, null, null, null);
    }
}
