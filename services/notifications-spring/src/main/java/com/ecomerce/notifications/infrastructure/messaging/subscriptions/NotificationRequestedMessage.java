package com.ecomerce.notifications.infrastructure.messaging.subscriptions;

import com.ecomerce.notifications.domain.valueobject.VariableValue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload del evento NotificationRequested (fuente: any-registered-system).
 * Llega en la EventEnvelope estándar de Keel (metadata + data).
 * Se reconoce por metadata.eventType == 'NotificationRequested': 'any-registered-system.events' transporta todos los eventos de any-registered-system, así que descarta el resto SIN lanzar excepción (una excepción dispara onFailure.retry y acaba mandando al descarte un mensaje válido que no era para ti).
 * Deduplica por metadata.eventId de la envoltura (lo estampa el emisor en el raise y viaja intacto) antes de despachar (la entrega es at-least-once).
 * Orden: IdempotencyGuard.alreadyProcessed(...) antes de despachar y record(...) DESPUÉS de que el handler termine bien. la clave de idempotencia participa en la clave natural del agregado, así que esa constraint ES la guarda —permanente y común a todas las puertas por las que entre la operación— y lo que no puede perderse es el mensaje: registrar ANTES haría que un fallo terminal se viera como «ya procesado» y su reintento no llegara nunca al descarte.
 * La deduplicación tiene VENTANA: el registro se purga a los processed-event.purge.retention-days (default 14, en parameters/), así que una reentrega posterior se procesa como nueva. Aquí es inocuo: la constraint de la clave natural sigue rechazándola, y esa no caduca.
 * Un «no existe» (NotFoundException) del caso de uso SÍ se reintenta: aquí suele ser la carrera de orden de un consumo asíncrono —el mensaje llega antes que el hecho del que depende— y el reintento la resuelve; agotado, va al descarte igual. Relánzalo tal cual (envuelto o no): lo reintenta la configuración que genera build. No lo confirmes ni lo mandes tú a la DLQ.
 * Identidad del emisor: resuelve requestedBy desde el campo 'metadata.source' del mensaje, y pásala YA RESUELTA a la operación. No la leas del payload: el DSL prohíbe que viaje ahí precisamente para que no haya dos versiones de la verdad. Se resuelve 1:1: el valor leído ES la clave natural del recurso que identifica.
 * Un emisor que no corresponda a nadie registrado va a la cola de descartes (onUnresolved: deadLetter): NO lo proceses y NO lo confirmes en silencio, porque el caso frecuente es un alta que falta y en silencio son efectos que no ocurren sin que nada dé error.
 * Con onFailure.deadLetter: tras agotar los reintentos el broker mueve el mensaje a notifications.any-registered-system-dlq. La topología la genera build — NO la declares tú.
 * Lo consume NotificationRequestedListener (listener del broker del stack; lo escribe el agente) despachando RequestNotificationCommand(requestedBy = la identidad resuelta, idempotencyKey = payload.idempotencyKey(), templateCode = payload.templateCode(), recipients = payload.recipients(), variables = payload.variables(), entryGate = EntryGate.SUBSCRIPTION) vía UseCaseMediator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequestedMessage(@Size(min = 8, max = 128) @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$") String idempotencyKey, @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String templateCode, @Size(min = 1, max = 20) List<String> recipients, @Size(max = 50) List<VariableValue> variables) {

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
        if (idempotencyKey == null) {
            throw new IllegalArgumentException(
                    "NotificationRequested: el mensaje no trae 'idempotencyKey', que el contrato declara obligatorio");
        }
        if (templateCode == null) {
            throw new IllegalArgumentException(
                    "NotificationRequested: el mensaje no trae 'templateCode', que el contrato declara obligatorio");
        }
        if (recipients == null) {
            throw new IllegalArgumentException(
                    "NotificationRequested: el mensaje no trae 'recipients', que el contrato declara obligatorio");
        }

        var violations = VALIDATOR.validate(this);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new IllegalArgumentException(
                    "NotificationRequested: el mensaje incumple el contrato en '" + first.getPropertyPath() + "': " + first.getMessage());
        }
    }
}
