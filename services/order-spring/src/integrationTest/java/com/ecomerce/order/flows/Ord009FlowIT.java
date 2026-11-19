package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord009FlowIT extends OrderFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @BeforeEach
    void given() {
        awaitPreconditions(this::seedCatalog);
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-009: errores de validación de negocio y su precedencia")
    void businessValidationAndPrecedence() {
        String both = paymentToken("tok") + "," + paymentMethod("cccccccc-0000-4000-8000-000000000001");

        assertError(createOrder(ana(), "k-ana-009a", orderBody("", line(P1, 1))), 400, "PAYMENT_SOURCE_INVALID");
        assertError(createOrder(ana(), "k-ana-009b", orderBody(both, line(P1, 1))), 400, "PAYMENT_SOURCE_INVALID");
        assertError(createOrder(ana(), "k-ana-009c", orderBody(paymentToken("tok"), line(P1, 1), line(P1, 2))), 422,
                "DUPLICATE_ORDER_LINE");
        assertError(createOrder(ana(), "k-ana-009d", orderBody(paymentToken("tok"), line(P1, 1), line(P9, 1))), 422,
                "PRODUCT_NOT_AVAILABLE");
        assertError(createOrder(ana(), "k-ana-009e", orderBody(paymentToken("tok"), line(P1, 1), line(P3, 1))), 422,
                "CURRENCY_MISMATCH");
        // Precedencia: la fuente de pago va antes que las guardas de las líneas.
        assertError(createOrder(ana(), "k-ana-009f", orderBody("", line(P9, 1), line(P9, 2))), 400,
                "PAYMENT_SOURCE_INVALID");
        // Precedencia: la existencia va antes que la moneda.
        assertError(createOrder(ana(), "k-ana-009g", orderBody(paymentToken("tok"), line(P9, 1), line(P3, 1))), 422,
                "PRODUCT_NOT_AVAILABLE");

        assertThat(charges()).as("payment no recibió ninguna llamada").isZero();
        Response list = get(ORDERS, ana());
        assertThat(list.status()).isEqualTo(200);
        Integer total = jsonPath(list, "$.totalElements");
        assertThat(total).isZero();
    }
}
