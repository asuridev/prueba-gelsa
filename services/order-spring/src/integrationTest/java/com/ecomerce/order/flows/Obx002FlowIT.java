package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Obx002FlowIT extends OrderFlowSupport {

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
    @DisplayName("FL-OBX-002: el encargo que el relay abandona no se pierde en silencio")
    void abandonedRequestIsNotLostSilently() {
        purgeMessages(NOTIFICATIONS);

        stopBroker();
        try {
            awaitBrokerStopped();
            Response created = createOrder(ana(), "k-ana-x02", orderBody(TOKEN_OK, line(P1, 1)));
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            assertThat(deadLetteredEvents()).isZero();

            // Se agota el presupuesto de reintentos con el canal todavía caído.
            abandonOutboxEvent("NotificationRequested");
            await(Duration.ofSeconds(30), () -> deadLetteredEvents() == 1);
        } finally {
            startBroker();
        }

        try {
            // Restablecido el canal, el encargo abandonado no sale (ni sale nada más).
            pause(Duration.ofSeconds(8));
            assertThat(publishedMessages(NOTIFICATIONS, 10).trim()).isEqualTo("[]");
            assertThat(deadLetteredEvents()).isEqualTo(1);
        } finally {
            clearAbandonedOutboxEvents();
        }
    }
}
