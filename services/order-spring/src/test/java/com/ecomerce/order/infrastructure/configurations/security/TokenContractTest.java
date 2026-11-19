package com.ecomerce.order.infrastructure.configurations.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * El contrato entre este servidor y el token del proveedor de identidad (keycloak): dado un token con la
 * forma que emite el proveedor, qué principal, qué authorities, qué identidad y qué exención ve el servidor. Los casos
 * los deriva el generador del diseño y del descriptor del proveedor; no necesitan el proveedor ni infraestructura.
 */
class TokenContractTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "none").claims(target -> target.putAll(claims)).build();
    }

    private static JwtAuthenticationToken authenticate(Jwt token) {
        return new JwtAuthenticationToken(token, List.of(), token.getSubject());
    }

    private static Set<String> authorities(Jwt token) {
        return authenticate(token).getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static void signIn(Jwt token) {
        SecurityContextHolder.getContext().setAuthentication(authenticate(token));
    }

    /** identity-claim */
    @Test
    void idtIdentityClaim() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "idt-identity"), Map.entry("preferred_username", "idt-user"), Map.entry("realm_access", Map.of("roles", List.of()))));
        signIn(token);
        Assertions.assertEquals("idt-identity", CallerIdentity.resolve());
    }
}
