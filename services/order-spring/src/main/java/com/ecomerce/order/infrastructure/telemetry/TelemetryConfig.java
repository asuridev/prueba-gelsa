package com.ecomerce.order.infrastructure.telemetry;

import ch.qos.logback.classic.LoggerContext;
import com.ecomerce.order.infrastructure.correlation.CorrelationContext;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Telemetría del servidor: lo que la autoconfiguración de Boot no hace sola.
 *
 * <p>El destino de las tres señales es UN colector OpenTelemetry (variable
 * {@code OTEL_EXPORTER_OTLP_ENDPOINT}, ver parameters/<perfil>/telemetry.yaml). El backend real
 * —Tempo, Jaeger, Loki, Prometheus, un SaaS— lo decide la configuración del colector, no este
 * servicio: cambiarlo no toca ni el código ni la configuración de la aplicación.
 */
@Configuration
public class TelemetryConfig {

    /**
     * Envía los logs al colector por OTLP, además de la consola.
     *
     * <p>Se instala POR CÓDIGO sobre el logger raíz y no con un {@code logback-spring.xml}: con
     * un XML, Boot deja de configurar la consola él mismo, y su {@code console-appender.xml} no
     * sabe de {@code logging.structured.format.console} — el JSON de develop y production
     * volvería a texto plano sin que nada fallara.
     *
     * <p>Cada registro lleva el contexto de traza del span activo (lo pone el appender) y el
     * {@code correlationId} del MDC como atributo: los dos hilos con los que se reconstruye una
     * petición.
     *
     * <p>Por {@code ObjectProvider}: donde la telemetría está apagada (el perfil {@code test}, un
     * {@code @SpringBootTest}) puede no haber SDK, y eso no puede impedir arrancar.
     *
     * <p>Y solo si {@code LOG_EXPORT_OTLP} lo pide: el canal primario de los logs es la consola,
     * que recoge la plataforma. Con los dos a la vez cada línea llegaría duplicada al backend, así
     * que el appender ni se cuelga del logger raíz si no se va a usar.
     */
    @Bean
    @ConditionalOnProperty(name = "management.logging.export.otlp.enabled", havingValue = "true")
    public InitializingBean openTelemetryLogAppender(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        return () -> {
            OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
            if (openTelemetry == null) {
                return;
            }
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            OpenTelemetryAppender appender = new OpenTelemetryAppender();
            appender.setContext(context);
            appender.setName("OTEL");
            appender.setOpenTelemetry(openTelemetry);
            appender.setCaptureMdcAttributes("correlationId");
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        };
    }

    /**
     * Fuera las peticiones al actuator y a las sondas del puerto principal (/livez, /readyz): llegan
     * cada pocos segundos y cada una sería una traza raíz sin nada dentro.
     */
    @Bean
    public ObservationPredicate ignoreActuator() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier() != null
                && isProbe(server.getCarrier().getRequestURI()));
    }

    private static boolean isProbe(String uri) {
        return uri.startsWith("/actuator") || uri.equals("/livez") || uri.equals("/readyz");
    }

    /**
     * Fuera las ejecuciones de las tareas programadas y las consultas SIN padre.
     *
     * <p>El relay del outbox corre cada segundo: sin esto, cada tick sería una traza raíz con
     * su consulta de reclamo dentro, aunque no haya nada que publicar. Lo que sí importa de una
     * tarea programada queda igual de visible: cada evento publicado abre su propia observación
     * ({@value MessageTracing#OUTBOX_PUBLISH}) colgada de la traza que lo originó, y cada
     * barrido pasa por el mediator, que abre la de su caso de uso — y el SQL de dentro tiene
     * entonces un padre y no se descarta.
     */
    @Bean
    public ObservationPredicate ignoreBackgroundNoise() {
        return (name, context) -> {
            if ("tasks.scheduled.execution".equals(name)) {
                return false;
            }
            boolean storeCall = name.startsWith("jdbc.") || name.startsWith("spring.data.mongodb") || name.startsWith("spring.data.redis");
            return !storeCall || hasRealParent(context);
        };
    }

    /**
     * Un padre NO-OP no cuenta como padre. Es justo el caso del relay: el tick programado que el
     * predicado de arriba descarta se queda como observación ACTUAL en forma de no-op, así que
     * sus consultas ven un padre no nulo — y sin esto salían como trazas raíz, una por segundo.
     * Medido: con {@code getParentObservation() != null} a secas, el colector recibía una
     * conexión JDBC raíz por cada tick del relay.
     */
    private static boolean hasRealParent(Observation.Context context) {
        return context.getParentObservation() instanceof Observation parent && !parent.isNoop();
    }

    /**
     * Avisa al ARRANCAR si las métricas no tienen por dónde salir.
     *
     * <p>Hay dos caminos y los dos se deciden por entorno: que un colector venga a leer
     * {@code /actuator/prometheus} (exposición + registro de Prometheus activos) o que el
     * servicio las empuje por OTLP ({@code METRICS_EXPORT_OTLP}). Si quien despliega
     * apaga los dos, no falla nada: el panel se queda vacío y las alertas, sin datos, no disparan.
     * Este aviso es lo único que hace visible ese estado, y lo hace en el primer minuto de vida del
     * proceso. Fuera del perfil {@code test}, donde las métricas se apagan a propósito.
     */
    @Bean
    @Profile("!test")
    public ApplicationListener<ApplicationReadyEvent> metricsPathCheck(Environment environment) {
        return event -> {
            String exposed = environment.getProperty("management.endpoints.web.exposure.include", "");
            boolean scrape = environment.getProperty("management.prometheus.metrics.export.enabled", Boolean.class, true)
                    && (exposed.contains("prometheus") || exposed.contains("*"));
            boolean push = environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class, false);
            if (!scrape && !push) {
                LoggerFactory.getLogger(TelemetryConfig.class).warn(
                        "Las métricas no tienen por dónde salir: ni /actuator/prometheus está expuesto "
                                + "(MANAGEMENT_ENDPOINTS, METRICS_EXPORT_PROMETHEUS) ni el envío por OTLP "
                                + "está encendido (METRICS_EXPORT_OTLP). El panel quedará vacío y las alertas no dispararán.");
            }
        };
    }

    /**
     * El correlationId como atributo del span HTTP y del de cada caso de uso, para encontrar una
     * traza por el id que recibió el cliente. Alta cardinalidad a propósito: va al SPAN, nunca a
     * una etiqueta de métrica.
     *
     * <p>En el span HTTP se lee de la cabecera de la RESPUESTA y no de CorrelationContext: la
     * observación del servidor se cierra después de que CorrelationFilter haya limpiado su
     * contexto, así que ahí ya no queda nada que leer.
     */
    @Bean
    public ObservationFilter correlationIdOnSpans() {
        return context -> {
            String correlationId = null;
            if (context instanceof ServerRequestObservationContext server && server.getResponse() != null) {
                correlationId = server.getResponse().getHeader("X-Correlation-Id");
            } else if ("keel.use-case".equals(context.getName())) {
                correlationId = CorrelationContext.get();
            }
            if (correlationId != null && !correlationId.isBlank()) {
                context.addHighCardinalityKeyValue(KeyValue.of("keel.correlation_id", correlationId));
            }
            return context;
        };
    }
}
