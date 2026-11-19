package com.ecomerce.notifications.infrastructure.configurations.audit;

import com.ecomerce.notifications.infrastructure.correlation.CorrelationContext;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Actor de las escrituras, para @CreatedBy/@LastModifiedBy.
 *
 * Nunca devuelve vacío: una columna de autoría sin valor obligaría a interpretar
 * el null, y las escrituras que no nacen de una petición son legítimas. En esos
 * casos el valor es el centinela {@code system}, no un usuario inventado.
 */
@Configuration
public class AuditorAwareConfig {

    /** Autor de las escrituras que no nacen de una petición autenticada. */
    private static final String SYSTEM = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken token) {
                // El sujeto de la credencial, no el principalClaim del converter: es el
                // identificador que no cambia aunque el usuario cambie de nombre.
                String subject = token.getToken().getSubject();
                return Optional.of(subject != null ? subject : token.getName());
            }
            // Escritura sin petición detrás (relay, listener, tarea programada): no hay
            // actor, pero sí traza. El centinela lleva la correlación para poder seguirla.
            String correlationId = CorrelationContext.get();
            return Optional.of(correlationId != null ? SYSTEM + ":" + correlationId : SYSTEM);
        };
    }
}
