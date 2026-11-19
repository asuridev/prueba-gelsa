package com.ecomerce.notifications.infrastructure.configurations.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Mapea el JWT del proveedor (keycloak) a authorities de Spring:
 * roles (ROLE_), scopes OAuth2 (SCOPE_) y permisos granulares (sin prefijo).
 * Claim de roles: realm_access.roles (anidado).
 * Principal: preferred_username.
 */
public class JwtAuthConverter {

    /** Permisos que otorga cada rol (security.roleGrants del diseño). */
    private static final java.util.Map<String, java.util.List<String>> ROLE_GRANTS = java.util.Map.ofEntries(
            java.util.Map.entry("notifications-admin", java.util.List.of("sender:read", "sender:write", "template:read", "template:write", "message:read", "suppression:read", "suppression:write", "retention:run")),
            java.util.Map.entry("template-editor", java.util.List.of("template:read", "template:write")),
            java.util.Map.entry("notifications-operator", java.util.List.of("template:read", "message:read", "suppression:read", "suppression:write")),
            java.util.Map.entry("notifications-auditor", java.util.List.of("sender:read", "template:read", "message:read", "suppression:read")));

    public JwtAuthenticationConverter converter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private java.util.Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        java.util.Collection<GrantedAuthority> roles = extractRoles(jwt);
        java.util.List<GrantedAuthority> authorities = new java.util.ArrayList<>(roles);
        authorities.addAll(extractScopes(jwt));
        authorities.addAll(extractPermissions(jwt));
        authorities.addAll(extractGrantedPermissions(roles));
        return authorities;
    }

    private java.util.Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        java.util.Map<String, Object> parent = jwt.getClaimAsMap("realm_access");
        if (parent == null) {
            return java.util.Collections.emptyList();
        }
        Object rolesObj = parent.get("roles");
        if (!(rolesObj instanceof java.util.List<?> roles)) {
            return java.util.Collections.emptyList();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private java.util.Collection<GrantedAuthority> extractScopes(Jwt jwt) {
        String scopeClaim = jwt.getClaimAsString("scope");
        if (scopeClaim == null || scopeClaim.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return java.util.List.of(scopeClaim.split(" ")).stream()
                .filter(s -> !s.isBlank())
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private java.util.Collection<GrantedAuthority> extractPermissions(Jwt jwt) {
        java.util.List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions == null || permissions.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return permissions.stream()
                .filter(p -> !p.isBlank())
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /**
     * Expande los roles del token a las authorities de permiso que otorgan, según
     * el catálogo roleGrants del diseño.
     */
    private java.util.Collection<GrantedAuthority> extractGrantedPermissions(java.util.Collection<GrantedAuthority> roles) {
        return roles.stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .flatMap(role -> ROLE_GRANTS.getOrDefault(role, java.util.List.of()).stream())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
