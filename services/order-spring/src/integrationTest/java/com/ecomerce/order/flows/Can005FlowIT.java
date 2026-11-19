package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Can005FlowIT extends OrderFlowSupport {

    private static final String ACTION = "{\"type\":\"redirect\",\"url\":\"https://3ds.example/c/9\"}";
    private String o1;

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("actionRequired", PAY1, null, ACTION);
        // Más que el timeoutMs (5000) de cancelPayment: no contesta en ninguno de sus intentos.
        stubTimeout("POST", "/payments/" + PAY1 + "/cancel", 6500);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            o1 = idOf(createdOrder(ana(), "k-ana-c05", orderBody(TOKEN_OK, line(P1, 1))));
        });
    }

    @Test
    @Order(1)
    @DisplayName("FL-CAN-005: payment no contesta a la anulación")
    void paymentDoesNotAnswerCancellation() {
        purgeMessages(NOTIFICATIONS);

        Response canceled = cancelOrder(ana(), o1);

        assertThat(canceled.status()).as(canceled.body()).isEqualTo(200);
        assertOrder(canceled, o1, "ana", "canceling", "EUR", "12.50", PAY1, ACTION, null, p1(1));
        assertNoEmails(Duration.ofSeconds(3));

        deliverPaymentCanceled("e-can-c5", paymentEvent(PAY1, o1, "ana", "12.50", "EUR", "\"requested\":true"));
        awaitStatusAna(o1, "canceled");
        assertEmailRequested(o1, "canceled", "order-canceled", "ana", "12.50", "EUR", null);

        // Rama condicional: payment responde failed a la anulación.
        stubCancel(PAY1, "failed", null);
        String o2 = idOf(createdOrder(ana(), "k-ana-c05b", orderBody(TOKEN_OK, line(P1, 1))));
        Response failed = cancelOrder(ana(), o2);
        assertThat(failed.status()).as(failed.body()).isEqualTo(200);
        assertOrder(failed, o2, "ana", "paymentFailed", "EUR", "12.50", PAY1, null, "processingError", p1(1));
        assertEmailRequested(o2, "payment-failed", "order-payment-failed", "ana", "12.50", "EUR", "processingError");
    }
}
