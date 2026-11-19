package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class CustomerPaymentsListFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    private static Map<String, Object> item(String ref, String customer) {
        return payment("chargeRequestId", ref, "customerRef", customer, "amount", 10.0, "currency", "EUR",
                "status", "authorized", "gatewayPaymentId", NOT_NULL);
    }

    @Test
    @Order(1)
    @DisplayName("FL-QRY-001: los cobros de un titular, paginados")
    void paymentsOfACustomerArePaged() {
        // Given: cus-7 con tres cobros en este orden, y cus-8 con uno.
        seedAuthorized("ch-201", "cus-7", "10.00");
        seedAuthorized("ch-202", "cus-7", "10.00");
        seedAuthorized("ch-203", "cus-7", "10.00");
        seedAuthorized("ch-204", "cus-8", "10.00");

        Map<String, Object> c201 = item("ch-201", "cus-7");
        Map<String, Object> c202 = item("ch-202", "cus-7");
        Map<String, Object> c203 = item("ch-203", "cus-7");

        // Del más reciente al más antiguo.
        assertPage(listPayments("customerRef=cus-7&page=0&size=2"), 0, 2, 3, 2, List.of(c203, c202));
        assertPage(listPayments("customerRef=cus-7&page=1&size=2"), 1, 2, 3, 2, List.of(c201));
        // Fuera de rango: página vacía canónica, aunque el titular tenga cobros en otras páginas.
        assertPage(listPayments("customerRef=cus-7&page=5&size=2"), 5, 2, 0, 0, List.of());
        assertPage(listPayments("customerRef=cus-9&page=0&size=2"), 0, 2, 0, 0, List.of());
        assertPage(listPayments("customerRef=cus-7&page=0&size=500"), 0, 100, 3, 1, List.of(c203, c202, c201));
        assertPage(listPayments("customerRef=cus-7"), 0, 20, 3, 1, List.of(c203, c202, c201));
    }
}
