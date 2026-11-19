package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Obx001FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("failed", PAY1, "declined", null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-OBX-001: el encargo de correo sobrevive a un canal indisponible")
    void emailRequestSurvivesUnavailableChannel() {
        purgeMessages(NOTIFICATIONS);
        String o1;
        String correlationId;

        stopBroker();
        try {
            awaitBrokerStopped();
            Response created = createOrder(ana(), "k-ana-x01", orderBody(TOKEN_OK, line(P1, 1)));

            assertThat(created.status()).as(created.body()).isEqualTo(201);
            o1 = idOf(created);
            correlationId = created.header("X-Correlation-Id");
            assertThat(correlationId).as("cabecera X-Correlation-Id de la respuesta").isNotBlank();
            assertOrder(created, o1, "ana", "paymentFailed", "EUR", "12.50", PAY1, null, "declined", p1(1));
            Response read = getOrder(ana(), o1);
            assertThat(read.status()).isEqualTo(200);
            String status = jsonPath(read, "$.status");
            assertThat(status).isEqualTo("paymentFailed");
            assertThat(publishedMessages(NOTIFICATIONS, 10).trim()).as("canal vacío durante la caída").isEqualTo("[]");
        } finally {
            startBroker();
        }

        String key = "order:" + o1 + ":payment-failed";
        await(Duration.ofSeconds(10), () -> !notificationsWithKey(key).isEmpty());
        holdsFor(Duration.ofSeconds(3), () -> notificationEnvelopes().size() == 1);
        List<String> found = notificationsWithKey(key);
        assertThat(found).hasSize(1);
        String relayedCorrelation = JsonPath.read(found.get(0), "$.metadata.correlationId");
        assertThat(relayedCorrelation).isEqualTo(correlationId);
        assertThat(deadLetteredEvents()).isZero();
    }
}
