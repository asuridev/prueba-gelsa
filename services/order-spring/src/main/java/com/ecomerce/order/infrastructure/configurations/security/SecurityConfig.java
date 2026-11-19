package com.ecomerce.order.infrastructure.configurations.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityErrorHandlers securityErrorHandlers;

    public SecurityConfig(SecurityErrorHandlers securityErrorHandlers) {
        this.securityErrorHandlers = securityErrorHandlers;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/livez", "/readyz", "/actuator/info", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders/{id}").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/orders").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/{id}/cancel").authenticated()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers).jwt(Customizer.withDefaults()));
        return http.build();
    }
}
