package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CorsCrossOriginFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedSender();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SEC-002: CORS - peticion normal cross-origin")
    void exposesCorrelationHeader() {
        // When: admin lee el remitente desde un origen permitido.
        Response response = exchangeWithHeaders(HttpMethod.GET, SENDER_PATH, null, tokenFor(ADMIN),
                Map.of("Origin", "http://localhost:3000"));

        // 1. 200, con X-Correlation-Id expuesta y presente.
        assertStatus(response, 200);
        String exposed = String.valueOf(response.header("Access-Control-Expose-Headers")).toLowerCase(Locale.ROOT);
        assertThat(exposed).contains("x-correlation-id");
        assertThat(response.header("X-Correlation-Id")).isNotBlank();
    }
}
