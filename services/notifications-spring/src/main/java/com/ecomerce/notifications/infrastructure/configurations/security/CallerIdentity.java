package com.ecomerce.notifications.infrastructure.configurations.security;

import com.ecomerce.notifications.domain.errors.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CallerIdentity {

    private CallerIdentity() {
    }

    /**
     * El identificador del recurso en cuyo nombre llega la petición, ya resuelto.
     *
     * <p>Lanza si no hay identidad: una operación que la necesita no puede continuar sin ella, y
     * seguir con un valor vacío escribiría datos a nombre de nadie. La cadena de seguridad ya
     * rechaza al no autenticado, así que llegar aquí sin token es un fallo de configuración.
     */
    public static String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException(
                "No hay credencial en el contexto: 'requestedBy' se resuelve de la identidad del llamante.");
        }
        Jwt jwt = token.getToken();
        String value = jwt.getClaimAsString("azp");
        if (value == null || value.isBlank()) {
            value = jwt.getClaimAsString("client_id");
        }
        if (value == null || value.isBlank()) {
            value = authentication.getName();
        }
        if (value == null || value.isBlank()) {
            // Un token válido sin el claim de la identidad no identifica a nadie: es un 401, no un fallo del servidor
            // (con un 500, la corrida de control de shop-api lo arregló a mano para FL-ACC-016).
            throw new UnauthorizedException(
                "Credenciales ausentes o no válidas", "UNAUTHENTICATED", 401, null);
        }
        return value;
    }
}
