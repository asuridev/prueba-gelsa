package com.ecomerce.order.infrastructure.messaging.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/**
 * Registro de lo ya procesado: un par (handler, evento) por cada mensaje que un
 * consumidor completó.
 *
 * La clave es compuesta a propósito: el mismo evento puede interesar a varios
 * listeners, y cada uno debe procesarlo exactamente una vez. La unicidad la
 * impone la clave primaria, no una consulta previa: es la BD la que arbitra la
 * carrera entre dos entregas simultáneas del mismo mensaje.
 *
 * <p>El índice sobre processed_at es de la purga, no de la lectura: el barrido
 * diario borra por rango de fecha y la clave primaria no le sirve de nada. Sin él
 * cada réplica hace un recorrido completo de la tabla a la misma hora. Mismo
 * nombre que su equivalente documental, para que las dos ramas se comparen.
 *
 * <p><b>Implementa {@link Persistable}</b>, y de eso depende que todo lo anterior
 * sea cierto. Con la clave ASIGNADA y sin esto, {@code SimpleJpaRepository.isNew()}
 * mira el id, lo ve no nulo, concluye que la fila ya existe y hace {@code merge()} —
 * un SELECT + UPDATE que NO viola la clave primaria y no lanza nada. El registro
 * dejaría de deduplicar por completo, en silencio: la carrera no la arbitraría nadie
 * y la reentrega se procesaría otra vez.
 */
@Entity
@Table(name = "processed_event", indexes = {
        @Index(name = "ix_processed_event_processed_at", columnList = "processed_at")
})
public class ProcessedEventJpa implements Persistable<ProcessedEventJpa.ProcessedEventId> {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Se pone a true al leer la fila de la base ({@code @PostLoad}). */
    @Transient
    private boolean persisted;

    protected ProcessedEventJpa() {
        // Requerido por JPA.
    }

    public ProcessedEventJpa(ProcessedEventId id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }

    @Override
    public ProcessedEventId getId() {
        return id;
    }

    /**
     * Nueva mientras no se haya leido de la base. NO es constante, y el matiz es
     * justo el que hace que esto funcione: {@code SimpleJpaRepository.delete(...)}
     * empieza con {@code if (isNew(entity)) return;}, asi que un {@code isNew()} que
     * devuelva siempre {@code true} convierte el borrado en un NO-OP SILENCIOSO.
     *
     * <p>Con el flag por {@code @PostLoad}: una fila recien construida es nueva
     * —{@code persist}, y la clave primaria arbitra la carrera, que es lo que este
     * registro necesita— y una fila leida de la base no lo es, de modo que borrarla
     * (retirar un registro caducado) funciona como en cualquier otra entidad.
     */
    @Override
    @Transient
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    /** Clave compuesta (handler, evento). */
    @Embeddable
    public static class ProcessedEventId implements Serializable {

        /** Identifica al consumidor; convención: nombre simple de la clase del listener. */
        @Column(name = "handler_id", nullable = false, length = 128)
        private String handlerId;

        /**
         * Identificador del mensaje: el messageId declarado en el diseño o
         * metadata.eventId.
         *
         * <p>255 y no 64: el id lo elige quien publica, y no siempre es un uuid — un
         * id compuesto o un MessageDeduplicationId de SQS FIFO se pasan de 64 con
         * facilidad. Y quedarse corto aquí no da un error de validación: da una
         * violación de longitud al insertar, es decir, un mensaje que se va a la DLQ
         * por no caber en la tabla que existe para no procesarlo dos veces. Las dos
         * columnas suman menos de 1600 bytes en utf8mb4, por debajo del límite de
         * índice de cualquier motor soportado.
         */
        @Column(name = "event_id", nullable = false, length = 255)
        private String eventId;

        protected ProcessedEventId() {
            // Requerido por JPA.
        }

        public ProcessedEventId(String handlerId, String eventId) {
            this.handlerId = handlerId;
            this.eventId = eventId;
        }

        public String getHandlerId() {
            return handlerId;
        }

        public String getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            ProcessedEventId that = (ProcessedEventId) other;
            return Objects.equals(handlerId, that.handlerId) && Objects.equals(eventId, that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(handlerId, eventId);
        }
    }
}
