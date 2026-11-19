package com.ecomerce.payment.infrastructure.configurations.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    @Value("${security.audience:payment}")
    private String audience;

    private final SecurityErrorHandlers securityErrorHandlers;

    public SecurityConfig(SecurityErrorHandlers securityErrorHandlers) {
        this.securityErrorHandlers = securityErrorHandlers;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/livez", "/readyz", "/actuator/info", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.POST, "/webhooks/payments").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAnyAuthority("SCOPE_payment:charge")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/capture").hasAnyAuthority("SCOPE_payment:charge")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/cancel").hasAnyAuthority("SCOPE_payment:charge")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/{id}/refunds").hasAnyAuthority("SCOPE_payment:refund")
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/{id}").hasAnyAuthority("SCOPE_payment:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/by-charge-request/{chargeRequestId}").hasAnyAuthority("SCOPE_payment:read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments").hasAnyAuthority("SCOPE_payment:read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payment-methods").hasAnyAuthority("SCOPE_payment-method:write")
                    .requestMatchers(HttpMethod.POST, "/api/v1/payment-methods/{paymentMethodId}/default").hasAnyAuthority("SCOPE_payment-method:write")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/payment-methods/{paymentMethodId}").hasAnyAuthority("SCOPE_payment-method:write")
                    .requestMatchers(HttpMethod.GET, "/api/v1/payment-methods").hasAnyAuthority("SCOPE_payment-method:read")
                    .anyRequest().hasAnyAuthority("SCOPE_payment:read"))
            .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(securityErrorHandlers).accessDeniedHandler(securityErrorHandlers).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
            .addFilterBefore(new AudienceAuthorizationFilter(audience), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        return new JwtAuthConverter().converter();
    }
}
