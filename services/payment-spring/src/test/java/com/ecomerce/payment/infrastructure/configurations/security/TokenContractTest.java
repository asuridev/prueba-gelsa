package com.ecomerce.payment.infrastructure.configurations.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
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
        return (JwtAuthenticationToken) new JwtAuthConverter().converter().convert(token);
    }

    private static Set<String> authorities(Jwt token) {
        return authenticate(token).getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static void signIn(Jwt token) {
        SecurityContextHolder.getContext().setAuthentication(authenticate(token));
    }

    private static void filterAudience() throws Exception {
        new AudienceAuthorizationFilter("payment").doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
    }

    /** scope-claim */
    @Test
    void idtScope() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "checkout"), Map.entry("azp", "checkout"), Map.entry("scope", "payment-method:read"), Map.entry("aud", "payment")));
        Assertions.assertTrue(authorities(token).contains("SCOPE_payment-method:read"), "falta SCOPE_payment-method:read");
    }

    /** audience-own */
    @Test
    void idtAudienceOwn() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "checkout"), Map.entry("azp", "checkout"), Map.entry("scope", "payment-method:read"), Map.entry("aud", "payment")));
        signIn(token);
        Assertions.assertDoesNotThrow(() -> filterAudience());
    }

    /** audience-foreign */
    @Test
    void idtAudienceForeign() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "checkout"), Map.entry("azp", "checkout"), Map.entry("scope", "payment-method:read"), Map.entry("aud", "audiencia-ajena")));
        signIn(token);
        Assertions.assertThrows(AccessDeniedException.class, () -> filterAudience());
    }
}
