package com.ecomerce.notifications.infrastructure.configurations;

import com.ecomerce.notifications.domain.parameters.NotificationsParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puebla {@link NotificationsParameters} desde la configuración por perfil.
 *
 * <p>Los placeholders van SIN default a propósito: el gradiente por perfil ya está en
 * {@code parameters/<perfil>/notifications.yaml}, y un default aquí lo
 * silenciaría justo donde importa —un despliegue de producción al que le falta la variable
 * arrancaría con un valor que nadie eligió en vez de no arrancar.
 */
@Configuration
public class NotificationsParametersConfig {

    @Bean
    public NotificationsParameters notificationsParameters(
            @Value("${notifications.sending-timeout-minutes}") Integer sendingTimeoutMinutes,
            @Value("${notifications.personal-data-retention-months}") Integer personalDataRetentionMonths) {
        return new NotificationsParameters(sendingTimeoutMinutes, personalDataRetentionMonths);
    }
}
