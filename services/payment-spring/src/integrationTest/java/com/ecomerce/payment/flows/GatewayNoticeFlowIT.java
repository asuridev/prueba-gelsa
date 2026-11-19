package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class GatewayNoticeFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-STL-008: el aviso de la pasarela")
    void gatewayNotice() {
        // Given: ch-110 autorizado que nadie captura, y ch-111 autorizado.
        String p110 = seedAuthorized("ch-110");
        String p111 = seedAuthorized("ch-111");

        // When: la pasarela avisa de que la autorización de ch-110 caducó.
        gatewayReports("ch-110", "CANCELED");
        Response notice = sendGatewayNotice("ch-110");
        assertThat(notice.status()).as(notice.body()).isLessThan(300);

        await(Duration.ofSeconds(10), () -> "canceled".equals(statusOf(p110)));
        assertEvent("PaymentCanceled", "ch-110", eventData(p110, "ch-110", "cus-1", 10.0, "requested", false));

        // When: un aviso sobre ch-111 con la firma alterada.
        Response forged = sendForgedGatewayNotice("ch-111");
        assertThat(forged.status()).as(forged.body()).isBetween(400, 499);
        assertThat(statusQueries("ch-111")).as("no consulta nada a la pasarela").isZero();
        assertThat(statusOf(p111)).isEqualTo("authorized");
        assertThat(eventsOf("PaymentCanceled", "ch-111")).isEmpty();
        assertThat(eventsOf("PaymentFailed", "ch-111")).isEmpty();
        assertThat(eventsOf("PaymentCaptured", "ch-111")).isEmpty();
    }
}
