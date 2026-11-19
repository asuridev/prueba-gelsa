package com.ecomerce.notifications.infrastructure.configurations.security;

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
        new AudienceAuthorizationFilter("notifications").doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
    }

    /** role-claim */
    @Test
    void idtRole() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "idt-sub"), Map.entry("preferred_username", "idt-user"), Map.entry("realm_access", Map.of("roles", List.of("notifications-admin")))));
        Assertions.assertEquals("idt-user", authenticate(token).getName());
        Assertions.assertTrue(authorities(token).contains("ROLE_notifications-admin"), "falta ROLE_notifications-admin");
        Assertions.assertTrue(authorities(token).contains("sender:read"), "falta sender:read");
        Assertions.assertTrue(authorities(token).contains("sender:write"), "falta sender:write");
        Assertions.assertTrue(authorities(token).contains("template:read"), "falta template:read");
        Assertions.assertTrue(authorities(token).contains("template:write"), "falta template:write");
        Assertions.assertTrue(authorities(token).contains("message:read"), "falta message:read");
        Assertions.assertTrue(authorities(token).contains("suppression:read"), "falta suppression:read");
        Assertions.assertTrue(authorities(token).contains("suppression:write"), "falta suppression:write");
        Assertions.assertTrue(authorities(token).contains("retention:run"), "falta retention:run");
    }

    /** no-role */
    @Test
    void idtNoRole() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "idt-sub"), Map.entry("preferred_username", "idt-user"), Map.entry("realm_access", Map.of("roles", List.of()))));
        Assertions.assertFalse(authorities(token).contains("ROLE_notifications-admin"), "sobra ROLE_notifications-admin");
        Assertions.assertFalse(authorities(token).contains("ROLE_notifications-auditor"), "sobra ROLE_notifications-auditor");
        Assertions.assertFalse(authorities(token).contains("ROLE_notifications-operator"), "sobra ROLE_notifications-operator");
        Assertions.assertFalse(authorities(token).contains("ROLE_template-editor"), "sobra ROLE_template-editor");
    }

    /** permission-claim */
    @Test
    void idtPermission() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "idt-sub"), Map.entry("preferred_username", "idt-user"), Map.entry("realm_access", Map.of("roles", List.of())), Map.entry("permissions", List.of("sender:write"))));
        Assertions.assertTrue(authorities(token).contains("sender:write"), "falta sender:write");
    }

    /** scope-claim */
    @Test
    void idtScope() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "billing"), Map.entry("azp", "billing"), Map.entry("scope", "bounce:report"), Map.entry("aud", "notifications")));
        Assertions.assertTrue(authorities(token).contains("SCOPE_bounce:report"), "falta SCOPE_bounce:report");
    }

    /** audience-own */
    @Test
    void idtAudienceOwn() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "billing"), Map.entry("azp", "billing"), Map.entry("scope", "bounce:report"), Map.entry("aud", "notifications")));
        signIn(token);
        Assertions.assertDoesNotThrow(() -> filterAudience());
    }

    /** audience-foreign */
    @Test
    void idtAudienceForeign() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "billing"), Map.entry("azp", "billing"), Map.entry("scope", "bounce:report"), Map.entry("aud", "audiencia-ajena")));
        signIn(token);
        Assertions.assertThrows(AccessDeniedException.class, () -> filterAudience());
    }

    /** identity-client */
    @Test
    void idtIdentityClient() {
        Jwt token = jwt(Map.ofEntries(Map.entry("sub", "billing"), Map.entry("azp", "billing"), Map.entry("scope", "bounce:report"), Map.entry("aud", "notifications")));
        signIn(token);
        Assertions.assertEquals("billing", CallerIdentity.resolve());
    }
}
