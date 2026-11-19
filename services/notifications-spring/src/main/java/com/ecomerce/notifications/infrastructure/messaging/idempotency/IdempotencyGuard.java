package com.ecomerce.notifications.infrastructure.messaging.idempotency;

import com.ecomerce.notifications.infrastructure.persistence.purge.BatchedPurge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Guarda de idempotencia del consumidor.
 *
 * La entrega de cualquier broker es at-least-once y el relay del outbox
 * reintenta, así que la reentrega no es un caso raro: es el comportamiento
 * normal ante cualquier corte. Lo que esta clase decide es qué hacer con ella.
 *
 * <p><b>Hay dos órdenes posibles y no son intercambiables.</b> El registro va en
 * su PROPIA transacción (REQUIRES_NEW), así que sobrevive al fallo del handler —
 * y eso es justo lo que hace que el orden importe:
 *
 * <ol>
 *   <li><b>Procesar y luego registrar</b> ({@link #alreadyProcessed} antes,
 *       {@link #record} después de que el handler termine bien). Es el
 *       <b>predeterminado</b>. Un fallo transitorio deja el mensaje sin marcar,
 *       el broker lo reentrega y se vuelve a intentar. El precio: si el proceso
 *       muere entre el commit del negocio y el registro, la reentrega se procesa
 *       dos veces — por eso este orden pide una guarda de dominio detrás (una
 *       transición de lifecycle que no admita repetición), que es la única que
 *       protege de verdad.</li>
 *   <li><b>Registrar y luego procesar</b> ({@link #tryRecord}, atómico). Cierra
 *       la ventana del duplicado, pero convierte cualquier fallo transitorio en
 *       un mensaje <b>perdido</b>: quedó marcado como procesado y nunca se
 *       ejecutó. Solo cuando reprocesar es inaceptable Y perder es tolerable.</li>
 * </ol>
 *
 * <p>Deshacer trabajo (una compensación) va casi siempre por el primer orden: lo
 * que no se puede perder es el mensaje que revierte algo real, y el guard del
 * agregado absorbe la repetición.
 *
 * <p><b>Con varias réplicas</b> la carrera deja de ser hipotética: dos entregas del
 * mismo mensaje pueden estar procesándose a la vez en dos procesos distintos. Nada
 * de aquí las coordina, y no hace falta — el árbitro es la clave primaria de la tabla, que es
 * compartida. Lo que sí importa es que la escritura y su interpretación estén en
 * lados distintos del proxy: por eso el registro vive en {@link ProcessedEventWriter}.
 *
 * <p>Cadencia y retención de la purga salen de parameters/, nunca del código.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventWriter writer;

    private final ProcessedEventJpaRepository processedEventRepository;

    @Value("${processed-event.purge.retention-days:14}")
    private int retentionDays;

    @Value("${processed-event.purge.batch-size:1000}")
    private int purgeBatchSize;

    @Value("${processed-event.purge.max-batches:500}")
    private int purgeMaxBatches;

    public IdempotencyGuard(ProcessedEventWriter writer, ProcessedEventJpaRepository processedEventRepository) {
        this.writer = writer;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * ¿Ya se procesó este mensaje? Consulta sin efectos, para descartar la
     * reentrega antes de despachar nada.
     *
     * @param handlerId identificador del consumidor (nombre simple del listener)
     * @param eventId   identificador del mensaje (messageId del diseño o metadata.eventId)
     * @return true si ya está registrado y hay que confirmarlo (ack) sin ejecutarlo
     */
    public boolean alreadyProcessed(String handlerId, String eventId) {
        boolean seen = writer.exists(new ProcessedEventJpa.ProcessedEventId(handlerId, eventId));
        if (seen) {
            logDuplicate(handlerId, eventId, "ya procesado");
        }
        return seen;
    }

    /**
     * Registra el mensaje como procesado. Se llama DESPUÉS de que el handler haya
     * terminado bien, en la transacción propia del writer para que el registro no
     * dependa de la del negocio (que ya commiteó).
     *
     * @return true si lo registró esta llamada; false si otra entrega simultánea
     *         llegó primero — no es un error, es la carrera resuelta por la BD
     */
    public boolean record(String handlerId, String eventId) {
        try {
            writer.insert(new ProcessedEventJpa.ProcessedEventId(handlerId, eventId));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // Dos entregas del mismo mensaje procesándose a la vez, normalmente en dos
            // réplicas distintas: la clave primaria es el árbitro y esta pierde. La transacción que revirtió es la del
            // writer, no la de quien llama: aquí no hay nada roto que arrastrar.
            logDuplicate(handlerId, eventId, "carrera resuelta en la clave");
            return false;
        }
    }

    /**
     * El log de frontera del descarte: una reentrega absorbida es un hecho normal (la entrega es
     * at-least-once) pero es lo primero que se busca cuando «el evento llegó y no pasó nada».
     * Solo ids, nunca el cuerpo del mensaje.
     */
    private static void logDuplicate(String handlerId, String eventId, String reason) {
        log.atInfo()
                .addKeyValue("keel.handler", handlerId)
                .addKeyValue("keel.event_id", eventId)
                .addKeyValue("keel.outcome", "duplicate")
                .log("Mensaje duplicado descartado por {} ({}): {}", handlerId, reason, eventId);
    }

    /**
     * Reclama el mensaje ANTES de procesarlo, de forma atómica. Ver la nota de la
     * clase: si el handler falla después de esto, el mensaje se pierde.
     *
     * <p>Es la MISMA inserción que {@link #record}: lo que cambia es cuándo se llama,
     * no cómo se escribe. No hay consulta previa a propósito — preguntar antes de
     * insertar no cierra ninguna ventana que la clave no cierre ya, y añade una
     * consulta por mensaje.
     *
     * @return true si es la primera vez y hay que procesar; false si es un duplicado
     */
    public boolean tryRecord(String handlerId, String eventId) {
        return record(handlerId, eventId);
    }

    public void purge() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = BatchedPurge.run("processed_event", purgeBatchSize, purgeMaxBatches,
                position -> processedEventRepository.findPurgeBoundary(cutoff, PageRequest.of(position, 1)),
                upTo -> processedEventRepository.deleteProcessedBefore(cutoff, upTo), cutoff);
        if (deleted > 0) {
            log.info("Idempotencia: purgadas {} filas procesadas antes de {}", deleted, cutoff);
        }
    }
}
