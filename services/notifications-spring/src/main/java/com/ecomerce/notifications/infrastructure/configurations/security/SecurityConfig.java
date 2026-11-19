package com.ecomerce.notifications.infrastructure.configurations.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.audience:notifications}")
    private String audience;

    private final SecurityErrorHandlers securityErrorHandlers;

    public SecurityConfig(SecurityErrorHandlers securityErrorHandlers) {
        this.securityErrorHandlers = securityErrorHandlers;
    }

    /**
     * Endpoints de audiencia services (clientes máquina): mismo resource server,
     * más la comprobación de audiencia como paso de AUTORIZACIÓN — un token de
     * usuario válido aquí da 403 (autenticado, sin permiso), no 401.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain serviceFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/notifications", "/api/v1/notifications/{idempotencyKey}", "/api/v1/messages/{messageId}/bounces")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/v1/notifications").hasAnyAuthority("SCOPE_notification:send")
                    .requestMatchers(HttpMethod.GET, "/api/v1/notifications/{idempotencyKey}").hasAnyAuthority("SCOPE_notification:read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/messages/{messageId}/bounces").hasAnyAuthority("SCOPE_bounce:report")
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
            .addFilterBefore(new AudienceAuthorizationFilter(audience), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/livez", "/readyz", "/actuator/info", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/sender-settings").hasAnyAuthority("sender:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/sender-settings").hasAnyAuthority("sender:read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/templates").hasAnyAuthority("template:write")
                    .requestMatchers(HttpMethod.POST, "/api/v1/templates/{templateCode}/versions").hasAnyAuthority("template:write")
                    .requestMatchers(HttpMethod.POST, "/api/v1/templates/{templateCode}/versions/{versionNumber}/activate").hasAnyAuthority("template:write")
                    .requestMatchers(HttpMethod.POST, "/api/v1/templates/{templateCode}/retire").hasAnyAuthority("template:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/templates/{templateCode}").hasAnyAuthority("template:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/templates").hasAnyAuthority("template:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/templates/{templateCode}/versions/{versionNumber}").hasAnyAuthority("template:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/templates/{templateCode}/versions").hasAnyAuthority("template:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messages/{id}").hasAnyAuthority("message:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/messages").hasAnyAuthority("message:read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/suppressions/{address}").hasAnyAuthority("suppression:write")
                    .requestMatchers(HttpMethod.POST, "/api/v1/suppressions/{address}/release").hasAnyAuthority("suppression:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/suppressions").hasAnyAuthority("suppression:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/suppressions/{address}").hasAnyAuthority("suppression:read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/maintenance/purge-personal-data").hasAnyAuthority("retention:run")
                    .anyRequest().hasAnyRole("notifications-admin"))
            .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        return new JwtAuthConverter().converter();
    }
}
