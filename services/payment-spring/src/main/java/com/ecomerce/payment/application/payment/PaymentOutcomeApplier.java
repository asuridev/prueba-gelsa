package com.ecomerce.payment.application.payment;

import com.ecomerce.payment.application.commands.MarkActionRequiredCommand;
import com.ecomerce.payment.application.commands.MarkAuthorizedCommand;
import com.ecomerce.payment.application.commands.MarkCanceledCommand;
import com.ecomerce.payment.application.commands.MarkCapturedCommand;
import com.ecomerce.payment.application.commands.MarkFailedCommand;
import com.ecomerce.payment.application.commands.MarkRefundedCommand;
import com.ecomerce.payment.application.port.out.CommandDispatcher;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import org.springframework.stereotype.Component;

/**
 * Aplica un desenlace de la pasarela con la operación del diseño que le corresponde
 * (payments.outcomes). Es el único sitio donde un {@link GatewayOutcome} se convierte en un cambio
 * del registro, venga de la respuesta síncrona, del aviso o del barrido.
 *
 * <p>Los handlers de esas operaciones tienen que ser <b>idempotentes</b>: el mismo desenlace puede
 * llegar por dos caminos, y uno tardío puede encontrar el cobro fuera del estado de origen. En los dos
 * casos no se hace nada y no es un error (docs/dsl/payments.md § Los desenlaces). Dos desenlaces
 * DISTINTOS a la vez los arbitra el bloqueo optimista.
 */
@Component
public class PaymentOutcomeApplier {

    private final CommandDispatcher dispatcher;

    public PaymentOutcomeApplier(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** PENDING y NOT_FOUND no son desenlaces: el llamante decide qué hacer con ellos. */
    public void apply(GatewayOutcome outcome) {
        switch (outcome.status()) {
            case AUTHORIZED -> dispatcher.dispatch(new MarkAuthorizedCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId()));
            case CAPTURED -> dispatcher.dispatch(new MarkCapturedCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId()));
            case FAILED -> dispatcher.dispatch(new MarkFailedCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId(),
                    outcome.failureReason()));
            case ACTION_REQUIRED -> dispatcher.dispatch(new MarkActionRequiredCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId(),
                    outcome.customerAction()));
            case REFUNDED -> dispatcher.dispatch(new MarkRefundedCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId(),
                    outcome.refundedAmount()));
            case CANCELED -> dispatcher.dispatch(new MarkCanceledCommand(
                    outcome.reference(),
                    outcome.gatewayPaymentId()));
            default -> {
                // Sin desenlace que aplicar.
            }
        }
    }
}
