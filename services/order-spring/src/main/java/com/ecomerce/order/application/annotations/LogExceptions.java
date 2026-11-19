package com.ecomerce.order.application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Loguea (y relanza) cualquier excepción del método anotado con el nivel
 * indicado; la implementa LogExceptionsAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExceptions {

    LogLevel level() default LogLevel.WARN;
}
