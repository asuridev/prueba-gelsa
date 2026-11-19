package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class CorsPreflightFlowIT extends NotificationsFlowBase {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SEC-001: CORS - preflight")
    void preflightIsAccepted() {
        // When: OPTIONS sin credencial, anunciando POST con Authorization y Content-Type.
        Response response = preflight(ROUTE_BASE + "/templates", ALLOWED_ORIGIN, "POST", "Authorization, Content-Type");

        // 1. Preflight aceptado, antes de la autorizacion (nunca 401).
        assertThat(response.status()).as("preflight: " + response.body()).isBetween(200, 204);
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.header("Access-Control-Allow-Methods")).contains("POST");
        String allowedHeaders = String.valueOf(response.header("Access-Control-Allow-Headers")).toLowerCase(Locale.ROOT);
        assertThat(allowedHeaders).contains("authorization").contains("content-type");
        assertThat(response.header("Access-Control-Max-Age")).isEqualTo("3600");
        String credentials = response.header("Access-Control-Allow-Credentials");
        assertThat(credentials == null || "false".equalsIgnoreCase(credentials)).isTrue();
    }
}
