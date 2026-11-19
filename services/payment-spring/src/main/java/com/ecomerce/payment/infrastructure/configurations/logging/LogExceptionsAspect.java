package com.ecomerce.payment.infrastructure.configurations.logging;

import com.ecomerce.payment.application.annotations.LogExceptions;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * OJO con el sitio donde se captura la firma: va ANTES del proceed(), no dentro del catch.
 *
 * getSignature() carga MethodInvocationProceedingJoinPoint$MethodSignatureImpl la primera vez
 * que se llama. Dentro del catch esa primera vez es la primera EXCEPCIÓN que el aspecto captura,
 * y si cae durante el apagado el LaunchedURLClassLoader ya cerró los jars anidados: el logging
 * del error muere con un NoClassDefFoundError y se lleva por delante justo el diagnóstico que se
 * estaba escribiendo. Sacarla fuera la carga en la primera invocación normal y cierra la ventana.
 *
 * Esto es una MITIGACIÓN DE UNA HIPÓTESIS, no un fallo reproducido. Lo observado: un solo
 * NoClassDefFoundError de esa clase, durante el apagado, en una corrida. Lo descartado: el
 * empaquetado (la clase está en el spring-aop anidado del fat jar y solo hay un spring-aop) y el
 * drenaje de las tareas @Scheduled (SchedulingConfig ya espera a que terminen). Lo que no se ha
 * podido reproducir: el fallo en sí, así que ningún escenario lo ve y ninguna mutación lo mide.
 */
@Aspect
@Component
public class LogExceptionsAspect {

    private static final Logger log = LoggerFactory.getLogger(LogExceptionsAspect.class);

    @Around("@annotation(logExceptions)")
    public Object logExceptions(ProceedingJoinPoint joinPoint, LogExceptions logExceptions) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        try {
            return joinPoint.proceed();
        } catch (Throwable exception) {
            // El detalle del motor puede llevar el valor de un campo personal (DSL 2.28): de una excepción de acceso a
            // datos solo sale su clase. La traduce después el ApiExceptionHandler, que no lo loguea.
            String detail = exception instanceof DataAccessException
                    ? "(detalle del motor omitido: puede llevar datos personales)"
                    : exception.getMessage();
            switch (logExceptions.level()) {
                // Sin la pila, a propósito: la imprime UNA vez el adaptador por el que entró el
                // fallo (ApiExceptionHandler, el contenedor del listener, el scheduler). Con ella
                // aquí cada fallo salía con dos pilas idénticas en el mismo log.
                case TRACE -> log.trace("Excepción en {}: {} ({})", method, detail, exception.getClass().getSimpleName());
                case DEBUG -> log.debug("Excepción en {}: {} ({})", method, detail, exception.getClass().getSimpleName());
                case INFO -> log.info("Excepción en {}: {} ({})", method, detail, exception.getClass().getSimpleName());
                case WARN -> log.warn("Excepción en {}: {} ({})", method, detail, exception.getClass().getSimpleName());
            }
            throw exception;
        }
    }
}
