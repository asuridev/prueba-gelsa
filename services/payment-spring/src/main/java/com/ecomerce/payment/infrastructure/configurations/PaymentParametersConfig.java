package com.ecomerce.payment.infrastructure.configurations;

import com.ecomerce.payment.domain.parameters.PaymentParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puebla {@link PaymentParameters} desde la configuración por perfil.
 *
 * <p>Los placeholders van SIN default a propósito: el gradiente por perfil ya está en
 * {@code parameters/<perfil>/payment.yaml}, y un default aquí lo
 * silenciaría justo donde importa —un despliegue de producción al que le falta la variable
 * arrancaría con un valor que nadie eligió en vez de no arrancar.
 */
@Configuration
public class PaymentParametersConfig {

    @Bean
    public PaymentParameters paymentParameters(
            @Value("${payment.action-required-timeout-hours}") Integer actionRequiredTimeoutHours) {
        return new PaymentParameters(actionRequiredTimeoutHours);
    }
}
