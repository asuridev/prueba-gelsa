package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class OutboxChannelDownFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-OBX-001: el desenlace sobrevive a un canal indisponible")
    @SuppressWarnings("unchecked")
    void outcomeSurvivesAnUnavailableChannel() {
        boolean restored = false;
        try {
            // Given: el canal sin mensajes y el canal de eventos indisponible.
            gatewayAuthorizes("ch-500");
            purgeMessages(EVENTS);
            stopBroker();
            awaitBrokerStopped();

            // When: se pide el cobro.
            Response created = charge("ch-500", "cus-1", "10.00", "EUR", tokenSource());

            // Then: la indisponibilidad no llega al consumidor y el canal sigue vacío.
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            String p500 = idOf(created);
            String correlationId = created.header("X-Correlation-Id");
            assertThat(correlationId).as("correlación de la petición").isNotBlank();
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("authorized");
            assertThat(statusOf(p500)).isEqualTo("authorized");
            assertThat(publishedMessages(EVENTS, 10).trim()).isEqualTo("[]");

            // When: el canal vuelve.
            startBroker();
            restored = true;

            // Then: en ≤ 10 s sale exactamente un PaymentAuthorized, con la correlación del primer paso.
            await(Duration.ofSeconds(15), () -> eventsOf("PaymentAuthorized", "ch-500").size() >= 1);
            List<Map<String, Object>> events = awaitEvents("PaymentAuthorized", "ch-500", 1);
            Map<String, Object> meta = (Map<String, Object>) events.get(0).get("metadata");
            assertThat(meta.get("correlationId")).isEqualTo(correlationId);
            assertThat(deadLetteredEvents()).as("ningún evento abandonado").isZero();
        } finally {
            if (!restored) {
                startBroker();
            }
        }
    }
}
