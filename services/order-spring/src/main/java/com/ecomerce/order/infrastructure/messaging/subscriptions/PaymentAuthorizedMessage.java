package com.ecomerce.order.infrastructure.messaging.subscriptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;

/**
 * Payload del evento PaymentAuthorized (fuente: payment).
 * Llega en la EventEnvelope estándar de Keel (metadata + data).
 * Se reconoce por metadata.eventType == 'PaymentAuthorized': 'payment.events' transporta todos los eventos de payment, así que descarta el resto SIN lanzar excepción (una excepción dispara onFailure.retry y acaba mandando al descarte un mensaje válido que no era para ti). En este diseño el destino lo comparten PaymentActionRequired, PaymentCaptured, PaymentFailed, PaymentCanceled, PaymentActionRejected.
 * Deduplica por metadata.eventId de la envoltura (lo estampa el emisor en el raise y viaja intacto) antes de despachar (la entrega es at-least-once).
 * Orden: IdempotencyGuard.alreadyProcessed(...) antes de despachar y record(...) DESPUÉS de que el handler termine bien. la operación declara transiciones, así que la repetición la frena el agregado y lo que no puede perderse es el mensaje: registrar ANTES haría que un fallo terminal se viera como «ya procesado» y su reintento no llegara nunca al descarte.
 * La deduplicación tiene VENTANA: el registro se purga a los processed-event.purge.retention-days (default 14, en parameters/), así que una reentrega posterior se procesa como nueva. Aquí es inocuo: la transición del agregado sigue rechazándola, y esa no caduca.
 * Un «no existe» (NotFoundException) del caso de uso SÍ se reintenta: aquí suele ser la carrera de orden de un consumo asíncrono —el mensaje llega antes que el hecho del que depende— y el reintento la resuelve; agotado, va al descarte igual. Relánzalo tal cual (envuelto o no): lo reintenta la configuración que genera build. No lo confirmes ni lo mandes tú a la DLQ.
 * Compite con createOrder, cancelOrder, applyPaymentActionRequired, applyPaymentFailed, applyPaymentCanceled, applyPaymentActionRejected, reconcileStalledOrders: sacan la entidad del mismo estado. Si al despachar la transición se rechaza porque otro llegó antes, es la carrera resuelta y NO un fallo — confirma el mensaje y no lo reintentes.
 * Se rechaza de DOS formas y las dos son esta carrera: InvalidStateTransitionException (otro camino ya movió el estado) y OptimisticLockingFailureException (llegasteis a la vez y perdió el commit) — captúralas juntas, la segunda con la base de org.springframework.dao, no con la de JPA ni la de Mongo.
 * Esa rama termina igualmente en IdempotencyGuard.record(...): el mensaje quedó atendido —por el otro camino— y sin registrarlo cada reentrega vuelve a atravesar el dominio para acabar en el mismo catch.
 * Con onFailure.deadLetter: tras agotar los reintentos el broker mueve el mensaje a order.payment-dlq. La topología la genera build — NO la declares tú.
 * Lo consume PaymentAuthorizedListener (listener del broker del stack; lo escribe el agente) despachando ApplyPaymentAuthorizedCommand(chargeRequestId = payload.chargeRequestId(), paymentId = payload.paymentId()) vía UseCaseMediator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentAuthorizedMessage(UUID paymentId, String chargeRequestId) {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * Contrato de la fuente: los campos que el diseño declara obligatorios tienen que
     * venir. Llámalo DESPUÉS de filtrar por {@code metadata.eventType} — un mensaje
     * ajeno del canal compartido se descarta SIN lanzar, y lanzar aquí lo mandaría al
     * descarte. Un payload que incumple el contrato (un campo que falta o fuera de cota)
     * lanza {@code IllegalArgumentException}, que va al descarte SIN reintentos: no se va a
     * volver válido reintentándolo. En SQS no hay exclusión por tipo: ahí manda el
     * maxReceiveCount de la cola.
     */
    public void requireContract() {
        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "PaymentAuthorized: el mensaje no trae 'paymentId', que el contrato declara obligatorio");
        }
        if (chargeRequestId == null) {
            throw new IllegalArgumentException(
                    "PaymentAuthorized: el mensaje no trae 'chargeRequestId', que el contrato declara obligatorio");
        }

        var violations = VALIDATOR.validate(this);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new IllegalArgumentException(
                    "PaymentAuthorized: el mensaje incumple el contrato en '" + first.getPropertyPath() + "': " + first.getMessage());
        }
    }
}
