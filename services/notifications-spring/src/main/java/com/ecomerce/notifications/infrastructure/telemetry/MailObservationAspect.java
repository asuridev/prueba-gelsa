package com.ecomerce.notifications.infrastructure.telemetry;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Observa cada entrega de correo al proveedor: un span con su duración dentro de la traza que la provocó, y su timer.
 *
 * <p>Sin esto, una petición cuyo tiempo se va aquí enseña un HUECO en la cascada de spans —el
 * caso de uso tarda y nada explica por qué—, que es peor que no tener traza: lleva a buscar el
 * tiempo donde no está.
 *
 * <p>Se enciende y se apaga por ENTORNO ({@code TELEMETRY_INSTRUMENT_MAIL}). El interruptor se resuelve al
 * construir el contexto, así que cambia con un reinicio y nunca en caliente; apagado, este bean
 * no existe y el puerto se invoca sin nada en medio.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "keel.telemetry.instrumentation.mail.enabled", havingValue = "true", matchIfMissing = true)
public class MailObservationAspect {

    private final ObservationRegistry observationRegistry;

    MailObservationAspect(ObjectProvider<ObservationRegistry> observationRegistry) {
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    /**
     * <p>La firma se lee ANTES del {@code proceed()}, nunca dentro del {@code catch}: es la misma
     * lección que dejó escrita {@code LogExceptionsAspect}. Ahí es donde carga por primera vez la
     * implementación de la firma, y si esa primera vez cae durante el apagado, el diagnóstico que
     * se estaba escribiendo se lo lleva un {@code NoClassDefFoundError}.
     */
    @Around("execution(* com.ecomerce.notifications.application.port.out.MailSender+.*(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getName();
        Observation observation = Observation.createNotStarted("keel.mail.send", observationRegistry)
                .contextualName("keel.mail.send")
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            Object result = joinPoint.proceed();
            observation.lowCardinalityKeyValue("keel.outcome", "ok");
            return result;
        } catch (Throwable error) {
            observation.lowCardinalityKeyValue("keel.outcome", "error");
            observation.error(error);
            throw error;
        } finally {
            observation.stop();
        }
    }
}
