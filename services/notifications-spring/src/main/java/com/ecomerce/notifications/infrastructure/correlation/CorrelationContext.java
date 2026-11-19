package com.ecomerce.notifications.infrastructure.correlation;

import com.ecomerce.notifications.domain.events.EventMetadata;
import com.ecomerce.notifications.infrastructure.telemetry.MessageTracing;
import io.micrometer.observation.transport.Kind;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Contexto de correlación del hilo actual.
 *
 * El valor se escribe a la vez en un ThreadLocal y en el MDC de SLF4J: el
 * primero lo lee el código (el bridge de eventos, al estampar la EventEnvelope)
 * y el segundo hace que aparezca en cada línea de log sin pasarlo por parámetro.
 *
 * Lo abre CorrelationFilter en la entrada HTTP; en la entrada asíncrona lo abre
 * el listener del broker con {@link #runWith(String, Runnable)}, de modo que los
 * eventos que provoque el consumo hereden la correlación del mensaje de origen.
 *
 * Los hilos son de un pool y se reutilizan: quien abre el contexto SIEMPRE debe
 * cerrarlo, o la próxima petición atendida por ese hilo heredará una
 * correlación ajena. Por eso runWith es preferible a set/clear a mano.
 */
public final class CorrelationContext {

    /** Clave con la que el correlationId aparece en el MDC y en el patrón de log. */
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
        // Clase de utilidad.
    }

    /**
     * Lo que se acepta como correlationId. El valor llega de FUERA (una cabecera HTTP, la
     * metadata de un mensaje ajeno) y acaba en cada línea de log, en la cabecera de respuesta,
     * en el atributo del span y en los eventos que salen: sin cota, un cliente podía meter
     * saltos de línea en los logs de texto o un valor de kilobytes en cada registro.
     */
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Fija la correlación del hilo actual; no hace nada si viene nula o en blanco. Un valor que no
     * cumple el formato no se rechaza ni se trunca: se sustituye por uno nuevo, igual que si no
     * hubiera llegado, porque truncar produciría ids que se confunden entre sí.
     */
    public static void set(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        String accepted = ACCEPTED.matcher(correlationId).matches() ? correlationId : UUID.randomUUID().toString();
        CURRENT.set(accepted);
        MDC.put(MDC_KEY, accepted);
    }

    /** @return la correlación del hilo actual, o null si no hay ninguna abierta. */
    public static String get() {
        return CURRENT.get();
    }

    /** Cierra el contexto. Va siempre en un finally. */
    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    /**
     * Ejecuta la acción con la correlación indicada y cierra el contexto pase lo
     * que pase. Es la forma que deben usar los listeners del broker.
     */
    public static void runWith(String correlationId, Runnable action) {
        try {
            set(correlationId);
            action.run();
        } finally {
            clear();
        }
    }

    /**
     * La forma que deben usar los listeners de mensajes con envoltura keel: abre la
     * correlación del mensaje de origen.
     * Con telemetría también continúa la traza W3C que el emisor estampó en la
     * envoltura; sin ella solo abre la correlación. Usarla siempre, en vez de la de
     * String, es lo que hace que activar la telemetría no exija tocar el listener.
     */
    public static void runWith(EventMetadata metadata, Runnable action) {
        if (metadata == null) {
            action.run();
            return;
        }
        runWith(metadata.correlationId(), () -> MessageTracing.continueFrom(
                metadata.traceparent(), MessageTracing.MESSAGE_CONSUME, Kind.CONSUMER, metadata.eventType(), action));
    }
}
