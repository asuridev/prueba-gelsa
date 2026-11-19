package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class OutboxAbandonedFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-OBX-002: el desenlace que el relay abandona no se pierde en silencio")
    void abandonedOutcomeIsNotLostInSilence() {
        boolean restored = false;
        try {
            // Given: el canal indisponible y un cobro autorizado con su PaymentAuthorized pendiente.
            gatewayAuthorizes("ch-501");
            purgeMessages(EVENTS);
            long abandonedBefore = deadLetteredEvents();
            stopBroker();
            awaitBrokerStopped();
            Response created = charge("ch-501", "cus-1", "10.00", "EUR", tokenSource());
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("authorized");

            // When: se agota el presupuesto de reintentos de ese evento.
            abandonOutboxEvent("PaymentAuthorized");

            // Then: el servidor informa de un evento abandonado.
            await(Duration.ofSeconds(60), () -> deadLetteredEvents() == abandonedBefore + 1);

            // Restablecido el canal, ese evento no se publica y el canal no recibe nada más.
            startBroker();
            restored = true;
            Thread.sleep(5000);
            assertThat(publishedMessages(EVENTS, 10).trim()).as("el canal sigue vacío").isEqualTo("[]");
            assertThat(deadLetteredEvents()).isEqualTo(abandonedBefore + 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Espera interrumpida", e);
        } finally {
            clearAbandonedOutboxEvents();
            if (!restored) {
                startBroker();
            }
        }
    }
}
