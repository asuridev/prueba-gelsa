package com.ecomerce.order.infrastructure.configurations;

import com.ecomerce.order.domain.parameters.OrderParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puebla {@link OrderParameters} desde la configuración por perfil.
 *
 * <p>Los placeholders van SIN default a propósito: el gradiente por perfil ya está en
 * {@code parameters/<perfil>/order.yaml}, y un default aquí lo
 * silenciaría justo donde importa —un despliegue de producción al que le falta la variable
 * arrancaría con un valor que nadie eligió en vez de no arrancar.
 */
@Configuration
public class OrderParametersConfig {

    @Bean
    public OrderParameters orderParameters(
            @Value("${order.action-required-timeout-hours}") Integer actionRequiredTimeoutHours,
            @Value("${order.payment-pending-timeout-minutes}") Integer paymentPendingTimeoutMinutes,
            @Value("${order.payment-action-retry-minutes}") Integer paymentActionRetryMinutes) {
        return new OrderParameters(actionRequiredTimeoutHours, paymentPendingTimeoutMinutes, paymentActionRetryMinutes);
    }
}
