package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Pay001FlowIT extends OrderFlowSupport {

    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("pending", PAY1, null, null);
        stubCapture(PAY1, "captured");
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            Response created = createdOrder(ana(), "k-ana-p01", orderBody(TOKEN_OK, line(P1, 2)));
            o1 = idOf(created);
            String status = jsonPath(created, "$.status");
            assertThat(status).isEqualTo("pendingPayment");
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-PAY-001: autorizado, captura y pagado, con su correo")
    void authorizedCaptureThenPaid() {
        purgeMessages(NOTIFICATIONS);

        deliverPaymentAuthorized("e-auth-1", authorizedEvent(o1, "25.00", "gw_1"));

        awaitStatusAna(o1, "authorized");
        Response authorized = getOrder(ana(), o1);
        String paymentId = jsonPath(authorized, "$.paymentId");
        Object action = jsonPath(authorized, "$.customerAction");
        assertThat(paymentId).isEqualTo(PAY1);
        assertThat(action).isNull();
        await(Duration.ofSeconds(20), () -> capturesOf(PAY1) == 1);

        // Reentrega del mismo evento: sin segunda captura ni cambio de estado.
        deliverPaymentAuthorized("e-auth-1", authorizedEvent(o1, "25.00", "gw_1"));
        holdsFor(Duration.ofSeconds(6), () -> capturesOf(PAY1) == 1 && "authorized".equals(statusOfAna(o1)));

        String captured = paymentEvent(PAY1, o1, "ana", "25.00", "EUR", null);
        deliverPaymentCaptured("e-cap-1", captured);

        awaitStatusAna(o1, "paid");
        Response paid = getOrder(ana(), o1);
        String paidPayment = jsonPath(paid, "$.paymentId");
        Object failure = jsonPath(paid, "$.failureReason");
        Object paidAction = jsonPath(paid, "$.customerAction");
        assertThat(paidPayment).isEqualTo(PAY1);
        assertThat(failure).isNull();
        assertThat(paidAction).isNull();
        assertEmailRequested(o1, "paid", "order-paid", "ana", "25.00", "EUR", null);

        deliverPaymentCaptured("e-cap-1", captured);
        holdsFor(Duration.ofSeconds(6),
                () -> "paid".equals(statusOfAna(o1)) && notificationCountOf(o1) == 1);

        // Un cobro de otro cliente de payment se ignora sin error.
        String foreign = "{\"paymentId\":\"" + PAY1 + "\",\"chargeRequestId\":\"checkout:xyz\",\"customerRef\":\"ana\","
                + "\"amount\":25.00,\"currency\":\"EUR\",\"gatewayPaymentId\":\"gw_x\"}";
        deliverPaymentAuthorized("e-auth-foreign", foreign);
        holdsFor(Duration.ofSeconds(6), () -> capturesOf(PAY1) == 1);
        assertThat(deadLetterMessages("PaymentAuthorized", 1)).isEmpty();

        // Caso borde: PaymentCaptured sin autorizar antes no hace nada.
        Response other = createdOrder(ana(), "k-ana-p01-b", orderBody(TOKEN_OK, line(P1, 1)));
        String o2 = idOf(other);

        deliverPaymentCaptured("e-cap-orphan", paymentEvent(PAY1, o2, "ana", "12.50", "EUR", null));

        holdsFor(Duration.ofSeconds(6),
                () -> "pendingPayment".equals(statusOfAna(o2)) && notificationCountOf(o2) == 0);
    }
}
