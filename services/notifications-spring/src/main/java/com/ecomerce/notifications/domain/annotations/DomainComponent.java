package com.ecomerce.notifications.domain.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un componente de la capa domain (servicios de dominio) sin acoplarlo a anotaciones de
 * Spring; UseCaseConfig lo registra como bean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainComponent {
}
