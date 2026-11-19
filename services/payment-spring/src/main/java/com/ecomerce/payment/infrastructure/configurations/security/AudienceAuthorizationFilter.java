package com.ecomerce.payment.infrastructure.configurations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige que el claim aud del JWT incluya la audiencia de este servicio
 * (security.audience, por defecto "payment"). Un token válido emitido para
 * otro servicio queda autenticado pero no autorizado: 403, no 401.
 */
public class AudienceAuthorizationFilter extends OncePerRequestFilter {

    private final String audience;

    public AudienceAuthorizationFilter(String audience) {
        this.audience = audience;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && (token.getToken().getAudience() == null || !token.getToken().getAudience().contains(audience))) {
            throw new AccessDeniedException("El token no está emitido para la audiencia " + audience);
        }
        chain.doFilter(request, response);
    }
}
