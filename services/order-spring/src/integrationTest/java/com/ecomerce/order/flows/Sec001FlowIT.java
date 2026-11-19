package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class Sec001FlowIT extends OrderFlowSupport {

    private static final String ORIGIN = "http://localhost:3000";

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    /** JWT con `exp` en el pasado: sin firma válida, el servidor lo rechaza igual con 401. */
    private static String expiredToken() {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long past = Instant.now().minusSeconds(3600).getEpochSecond();
        String payload = url.encodeToString(("{\"sub\":\"ana\",\"email\":\"ana@example.com\",\"exp\":" + past + "}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + url.encodeToString("firma".getBytes(StandardCharsets.UTF_8));
    }

    private Response[] fourCalls(String token) {
        String body = orderBody(TOKEN_OK, line(P1, 1));
        return new Response[] {
            exchangeWithKey(HttpMethod.POST, ORDERS, body, token, "k-sec-001"),
            exchangeWithKey(HttpMethod.GET, ORDERS + "/" + MISSING_ORDER, null, token, null),
            exchangeWithKey(HttpMethod.GET, ORDERS, null, token, null),
            exchangeWithKey(HttpMethod.POST, ORDERS + "/" + MISSING_ORDER + "/cancel", null, token, "k-sec-001-c")
        };
    }

    @Test
    @Order(1)
    @DisplayName("FL-SEC-001: sin credencial y desde el navegador")
    void withoutCredentialAndFromBrowser() {
        for (Response response : fourCalls(null)) {
            assertError(response, 401, "UNAUTHENTICATED");
        }
        assertThat(charges()).isZero();

        for (Response response : fourCalls(expiredToken())) {
            assertThat(response.status()).as(response.body()).isEqualTo(401);
        }

        Response preflight = preflight(ORDERS, ORIGIN, "POST", "Authorization, Content-Type, Idempotency-Key");
        assertThat(preflight.status()).as("preflight sin credencial: " + preflight.body()).isBetween(200, 204);
        String methods = String.valueOf(preflight.header("Access-Control-Allow-Methods"));
        assertThat(methods).contains("POST");
        String headers = String.valueOf(preflight.header("Access-Control-Allow-Headers")).toLowerCase();
        assertThat(headers).contains("authorization").contains("content-type").contains("idempotency-key");
        assertThat(preflight.header("Access-Control-Max-Age")).isEqualTo("3600");
        assertThat(preflight.header("Access-Control-Allow-Credentials")).isNotEqualTo("true");

        Response cross = exchangeWithHeaders(HttpMethod.POST, ORDERS, orderBody(TOKEN_OK, line(P1, 1)), ana(),
                Map.of("Origin", ORIGIN));
        assertThat(cross.status()).as(cross.body()).isEqualTo(201);
        String exposed = String.valueOf(cross.header("Access-Control-Expose-Headers")).toLowerCase();
        assertThat(exposed).contains("location").contains("x-correlation-id");
    }
}
