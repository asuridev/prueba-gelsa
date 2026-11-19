package com.ecomerce.order.infrastructure.messaging.outbox;

import com.ecomerce.order.infrastructure.telemetry.MessageTracing;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.transport.Kind;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reenvía al broker las filas pendientes del outbox, ya fuera de la transacción
 * que las creó.
 *
 * <p><b>Tres pasos, y la publicación no está dentro de ninguna transacción:</b>
 * (1) {@link OutboxRelayStore#claimBatch} reclama el lote en una transacción corta;
 * (2) el dispatcher publica, sin conexión de base de datos retenida; (3)
 * {@code markPublished}/{@code markFailed} escriben el desenlace en otra transacción
 * corta. Meter (2) dentro de (1) es retener una conexión del pool durante I/O de red:
 * con el broker lento, el pool se agota y lo que falla es cualquier otra cosa que
 * necesitara una conexión en ese momento.
 *
 * Un fallo de entrega no revierte nada: incrementa el contador de intentos y la
 * fila se reintenta en la pasada siguiente (entrega at-least-once — el
 * consumidor deduplica por metadata.eventId). Una fila que agota maxAttempts
 * queda como dead-letter: se excluye de findPending y se reporta a ERROR, sin
 * borrarse, para inspección. Un cron diario purga lo ya publicado. Cadencias,
 * retención y tope de intentos salen de parameters/, nunca del código.
 */
@Component
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final OutboxRelayStore store;
    private final OutboxDispatcher dispatcher;
    private final MeterRegistry meterRegistry;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Value("${outbox.relay.max-attempts:10}")
    private int maxAttempts;

    @Value("${outbox.relay.backoff.initial-ms:1000}")
    private long backoffInitialMs;

    @Value("${outbox.relay.backoff.max-ms:60000}")
    private long backoffMaxMs;

    /**
     * Cuánto retiene el lease una fila reclamada mientras su publicación está en vuelo.
     * Tiene que cubrir con holgura el timeout de la llamada al broker: si se queda corto,
     * otra pasada recoge una fila que sigue despachándose y el consumidor recibe el evento
     * dos veces (lo absorbe su deduplicación, pero es trabajo de más). Si una réplica muere
     * con la fila en vuelo, el lease caduca y la siguiente pasada la recoge.
     */
    @Value("${outbox.relay.claim-timeout-ms:60000}")
    private long claimTimeoutMs;

    /** El sondeo de RESPALDO: lo normal es que despierte el commit (ver {@link #onAppended}). */
    @Value("${outbox.relay.poll-ms:30000}")
    private long pollMs;

    /** El interruptor de los relojes, el mismo que el del cronómetro: en {@code test} va apagado. */
    @Value("${scheduling.enabled:true}")
    private boolean enabled;

    private final Object monitor = new Object();
    /** Lo tiene la pasada mientras corre: {@link #pause()} lo toma para esperar a la que esté en vuelo. */
    private final ReentrantLock passLock = new ReentrantLock();
    private volatile boolean running;
    private volatile boolean paused;
    private boolean woken;
    /** El reintento más próximo que esta réplica programó y aún no ha llegado; {@code Long.MAX_VALUE}: ninguno. */
    private long pendingRetryMs = Long.MAX_VALUE;
    private volatile CountDownLatch stopped;
    private final AtomicLong passes = new AtomicLong();

    public OutboxRelay(OutboxEventJpaRepository outboxRepository, OutboxRelayStore store,
            OutboxDispatcher dispatcher, MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.store = store;
        this.dispatcher = dispatcher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * La señal de que el outbox se rindió.
     *
     * <p>Una fila que agota sus reintentos deja de reclamarse y se queda ahí: es pérdida
     * de datos en el mecanismo cuya ÚNICA promesa es que ningún evento se pierde. Sin
     * esto lo único que ocurre es un {@code log.error}, y un log no dispara nada.
     *
     * <p>Va como GAUGE y no como contador a propósito: un contador se reinicia con el
     * proceso y no ve las filas que se rindieron antes de arrancar, que son justo las que
     * llevan más tiempo perdidas. El gauge dice cuántas hay AHORA, que es la pregunta.
     *
     * <p>Sale por el endpoint {@code metrics} del actuator, ya expuesto: es lo que permite
     * alertar en producción, y lo que hace la rendición observable desde fuera.
     */
    @PostConstruct
    void registerDeadLetterGauge() {
        Gauge.builder("keel.outbox.dead_lettered", outboxRepository, repository -> repository.countDeadLettered(maxAttempts))
                .description("Filas del outbox que agotaron sus reintentos y siguen sin entregarse")
                .register(meterRegistry);
    }

    /**
     * Una pasada: reclama, publica y devuelve CUÁNDO correr la siguiente (ver {@link #nextRun}). La llama el bucle del
     * relay; visible para las pruebas.
     */
    public long relay() {
        long passStart = System.currentTimeMillis();
        long retryAt = Long.MAX_VALUE;
        // (1) Reclamo: transacción corta, solo base de datos.
        List<ClaimedOutboxEvent> claimed = store.claimBatch(maxAttempts, batchSize, claimTimeoutMs);
        for (ClaimedOutboxEvent row : claimed) {
            try {
                // (2) Publicación: FUERA de toda transacción. Ninguna conexión retenida.
                MessageTracing.continueFrom(MessageTracing.traceparentOfEnvelope(row.payload()), MessageTracing.OUTBOX_PUBLISH, Kind.PRODUCER, row.eventType(), () -> dispatcher.dispatch(row.destination(), row.routingKey(), row.eventType(), row.payload()));
                // (3) Desenlace, en su propia transacción corta.
                store.markPublished(row.id());
                log.atDebug()
                        .addKeyValue("keel.outbox.id", row.id())
                        .addKeyValue("keel.event_type", row.eventType())
                        .addKeyValue("keel.outcome", "published")
                        .log("Outbox: {} publicado", row.eventType());
            } catch (RuntimeException ex) {
                OutboxRelayStore.MarkFailedOutcome outcome =
                        store.markFailed(row.id(), truncate(ex.getMessage()), maxAttempts, backoffInitialMs, backoffMaxMs);
                if (outcome.deadLettered()) {
                    // Dead-letter: agotó los reintentos. Queda parada (fuera de futuros
                    // polls) para inspección manual; no se borra ni bloquea al resto.
                    log.error("Outbox: {} agotó {} reintentos y queda como dead-letter: {}",
                            row.id(), maxAttempts, ex.getMessage());
                } else {
                    retryAt = Math.min(retryAt, outcome.nextAttemptAtMs());
                    log.warn("Outbox: fallo entregando {} (intento {}): {}", row.id(), outcome.attempts(), ex.getMessage());
                }
            }
        }
        return nextRun(claimed.size() >= batchSize, retryAt, passStart);
    }

    /**
     * Cuándo corre la pasada siguiente ({@code relayNextRunMs} de keel-core/gen/timer-plan.js): en el acto con el lote
     * lleno (quedan pendientes); si no, al primero del reintento más próximo que esta réplica programó y del sondeo de
     * respaldo. Un reintento ya vencido al empezar la pasada lo reclamó ella, así que se olvida.
     */
    long nextRun(boolean full, long retryAtMs, long passStartMs) {
        long remembered = pendingRetryMs > passStartMs ? pendingRetryMs : Long.MAX_VALUE;
        pendingRetryMs = Math.min(remembered, retryAtMs);
        long now = System.currentTimeMillis();
        if (full) {
            return now;
        }
        return Math.min(pendingRetryMs, now + pollMs);
    }

    /** Una fila nueva confirmada en ESTA réplica: el relay sale ya, sin esperar al sondeo. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAppended(OutboxAppended appended) {
        wake();
    }

    /** Adelanta la pasada a AHORA. Un despertar durante una pasada no se pierde: la siguiente va sin dormir. */
    public void wake() {
        synchronized (monitor) {
            woken = true;
            monitor.notifyAll();
        }
    }

    /**
     * Para el relay de esta réplica y ESPERA a la pasada que esté en vuelo: al volver, no hay ninguna publicando. Lo usa
     * el arnés durante la resiembra del broker. Devuelve si había un relay en marcha que pausar.
     */
    public boolean pause() {
        synchronized (monitor) {
            paused = true;
            monitor.notifyAll();
        }
        passLock.lock();
        passLock.unlock();
        return running;
    }

    /** Lo reanuda, con una pasada en el acto: lo escrito durante la pausa despertó a un relay parado. */
    public void resume() {
        synchronized (monitor) {
            paused = false;
            woken = true;
            monitor.notifyAll();
        }
    }

    private void loop() {
        while (running) {
            synchronized (monitor) {
                while (running && paused) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                woken = false;
            }
            long next;
            passLock.lock();
            try {
                // La pausa pudo llegar entre la espera y el candado: entonces esta pasada no corre.
                if (paused || !running) {
                    continue;
                }
                passes.incrementAndGet();
                next = relay();
            } catch (RuntimeException failure) {
                log.error("La pasada del relay falló; se reintenta tras el sondeo de respaldo", failure);
                next = System.currentTimeMillis() + pollMs;
            } finally {
                passLock.unlock();
            }
            synchronized (monitor) {
                long remaining;
                while (running && !woken && !paused && (remaining = next - System.currentTimeMillis()) > 0) {
                    try {
                        monitor.wait(remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * El hilo lo da un ejecutor de Spring con hilos de PLATAFORMA, no un {@code Thread} a mano (lo veta
     * check-logging.sh), y empieza sin contexto, como el {@code @Scheduled} al que sustituye.
     */
    @Override
    public void start() {
        running = true;
        stopped = new CountDownLatch(1);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("order-outbox-relay-");
        executor.setVirtualThreads(false);
        executor.execute(() -> {
            try {
                loop();
            } finally {
                stopped.countDown();
            }
        });
    }

    /** Al apagar se espera a la pasada en vuelo: cortarla deja filas con su lease, que otra réplica tendría que esperar. */
    @Override
    public void stop() {
        running = false;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        if (stopped != null) {
            try {
                stopped.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return enabled;
    }

    /** Cuántas pasadas lleva el bucle de esta réplica: en reposo, una por sondeo de respaldo. */
    long passes() {
        return passes.get();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }
}
