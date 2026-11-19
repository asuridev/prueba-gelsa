package com.ecomerce.order.infrastructure.configurations.security;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Política CORS del servicio. Métodos derivados de los endpoints declarados en
 * el diseño (más OPTIONS, el preflight). Los orígenes permitidos se configuran
 * por ambiente en security.cors.allowed-origins: sin ninguno, el servicio no
 * acepta peticiones cross-origin.
 */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "OPTIONS", "POST");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Idempotency-Key");
    private static final List<String> EXPOSED_HEADERS = List.of("Location", "X-Correlation-Id");
    private static final Duration MAX_AGE = Duration.ofSeconds(3600);

    @Value("${security.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
