package com.ecomerce.order.infrastructure.messaging.subscriptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;

/**
 * Payload del evento ProductRetired (fuente: catalog).
 * Llega en la EventEnvelope estándar de Keel (metadata + data).
 * Se reconoce por metadata.eventType == 'ProductRetired': 'catalog.events' transporta todos los eventos de catalog, así que descarta el resto SIN lanzar excepción (una excepción dispara onFailure.retry y acaba mandando al descarte un mensaje válido que no era para ti). En este diseño el destino lo comparten ProductUpserted.
 * Deduplica por metadata.eventId de la envoltura (lo estampa el emisor en el raise y viaja intacto) antes de despachar (la entrega es at-least-once).
 * Orden: IdempotencyGuard.tryRecord(...) antes de despachar — la operación no declara ninguna guarda de dominio (ni transiciones, ni una clave de idempotencia sobre la clave natural) que frene la repetición, así que la ventana se cierra reclamando antes. Ojo: un fallo del handler deja el mensaje marcado y perdido; si eso no es tolerable, lo que falta es la guarda de dominio en el diseño.
 * La deduplicación tiene VENTANA: el registro se purga a los processed-event.purge.retention-days (default 14, en parameters/), así que una reentrega posterior se procesa como nueva. Aquí NO es inocuo: sin transición que la frene, pasada la retención el efecto se vuelve a aplicar. Si el negocio necesita una ventana mayor, el mecanismo correcto es una guarda de dominio, no esta deduplicación — y eso es un hueco del diseño, no algo que se arregle en el listener.
 * Con onFailure.deadLetter: tras agotar los reintentos el broker mueve el mensaje a order.catalog-dlq. La topología la genera build — NO la declares tú.
 * Lo consume ProductRetiredListener (listener del broker del stack; lo escribe el agente) despachando RemoveProductCommand(id = payload.productId(), sourceVersion = payload.version()) vía UseCaseMediator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductRetiredMessage(UUID productId, Long version) {

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
        if (productId == null) {
            throw new IllegalArgumentException(
                    "ProductRetired: el mensaje no trae 'productId', que el contrato declara obligatorio");
        }
        if (version == null) {
            throw new IllegalArgumentException(
                    "ProductRetired: el mensaje no trae 'version', que el contrato declara obligatorio");
        }

        var violations = VALIDATOR.validate(this);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new IllegalArgumentException(
                    "ProductRetired: el mensaje incumple el contrato en '" + first.getPropertyPath() + "': " + first.getMessage());
        }
    }
}
