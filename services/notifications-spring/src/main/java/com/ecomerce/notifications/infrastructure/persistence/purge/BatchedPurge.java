package com.ecomerce.notifications.infrastructure.persistence.purge;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Purga POR LOTES de las tablas del generador, cada lote en su propia transacción.
 *
 * <p>Un único DELETE sobre todo lo caducado es una transacción del tamaño del atraso: tras una
 * caída, millones de filas. Retiene sus bloqueos hasta el final —en SQL Server, pasadas unas miles
 * de filas, escala a bloqueo de TABLA y detiene toda escritura en ella—, llena el undo o el WAL, y
 * con el timeout de transacción del servicio no termina nunca: muere cada noche sin avanzar.
 *
 * <p>El lote se acota por INSTANTE y no por id: se lee el instante de la fila que ocupa la posición
 * N entre las que hay que purgar y se borra hasta él. Así vale igual para las claves compuestas,
 * recorre el índice que la tabla ya tiene sobre ese campo y es JPQL, el mismo en todos los motores.
 * Las filas que comparten instante con la frontera caen en el mismo lote, así que un lote puede
 * pasar de N: la cota es aproximada a propósito.
 *
 * <p>Corre en todas las réplicas a la vez y no se coordina: borrar es idempotente, y dos réplicas
 * que se solapan solo borran menos cada una.
 */
public final class BatchedPurge {

    private static final Logger log = LoggerFactory.getLogger(BatchedPurge.class);

    private BatchedPurge() {
    }

    /**
     * @param what       nombre de la tabla para el log
     * @param batchSize  filas por lote
     * @param maxBatches lotes por pasada; alcanzado, avisa y deja el resto a la siguiente
     * @param boundary   dada una posición, el instante de esa fila entre las que hay que purgar, o vacío
     * @param deleteUpTo borra lo anterior al corte hasta ese instante incluido, en su propia transacción
     * @param cutoff     el corte de retención
     * @return filas borradas en total
     */
    public static long run(String what, int batchSize, int maxBatches,
            IntFunction<List<Instant>> boundary, Function<Instant, Integer> deleteUpTo, Instant cutoff) {
        if (batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException(what + ": batch-size y max-batches tienen que ser positivos");
        }
        long total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<Instant> edge = boundary.apply(batchSize - 1);
            if (edge.isEmpty()) {
                // Queda menos de un lote: el último, entero.
                return total + deleteUpTo.apply(cutoff);
            }
            total += deleteUpTo.apply(edge.get(0));
        }
        log.atWarn()
                .addKeyValue("keel.purge.table", what)
                .log("{}: la purga llegó a su tope de {} lotes de {} filas y quedan filas por purgar; "
                        + "la siguiente pasada sigue", what, maxBatches, batchSize);
        return total;
    }
}
