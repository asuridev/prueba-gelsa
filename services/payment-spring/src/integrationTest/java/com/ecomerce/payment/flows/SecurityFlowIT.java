package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class SecurityFlowIT extends PaymentFlowSupport {

    private record Op(String name, HttpMethod method, String path, String body) {
    }

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-SEC-001: sin credencial, sin scope y con otra audiencia")
    void withoutCredentialWithoutScopeAndWithOtherAudience() {
        // Preparación: un cobro y un medio reales, y la evidencia afirmativa de que el canal entrega.
        String p = seedAuthorized("ch-600");
        String m = seedMethod("cus-1");
        awaitEvents("PaymentAuthorized", "ch-600", 1);

        String chargeBody = "{\"chargeRequestId\": \"ch-601\", \"customerRef\": \"cus-1\", \"amount\": 10.00, "
                + "\"currency\": \"EUR\", \"paymentToken\": \"tok_sec\"}";
        Op requestCharge = new Op("requestCharge", HttpMethod.POST, ROUTE_BASE + "/payments", chargeBody);
        Op capturePayment = new Op("capturePayment", HttpMethod.POST, ROUTE_BASE + "/payments/" + p + "/capture", null);
        Op cancelPayment = new Op("cancelPayment", HttpMethod.POST, ROUTE_BASE + "/payments/" + p + "/cancel", null);
        Op refundPayment = new Op("refundPayment", HttpMethod.POST, ROUTE_BASE + "/payments/" + p + "/refunds",
                "{\"refundRequestId\": \"rf-sec\", \"amount\": 1.00}");
        Op getPayment = new Op("getPayment", HttpMethod.GET, ROUTE_BASE + "/payments/" + p, null);
        Op getByCharge = new Op("getPaymentByChargeRequest", HttpMethod.GET,
                ROUTE_BASE + "/payments/by-charge-request/ch-600", null);
        Op listPayments = new Op("listPaymentsByCustomer", HttpMethod.GET,
                ROUTE_BASE + "/payments?customerRef=cus-1", null);
        Op savePaymentMethod = new Op("savePaymentMethod", HttpMethod.POST, ROUTE_BASE + "/payment-methods",
                "{\"customerRef\": \"cus-1\", \"paymentToken\": \"tok_sec_m\"}");
        Op listPaymentMethods = new Op("listPaymentMethods", HttpMethod.GET,
                ROUTE_BASE + "/payment-methods?customerRef=cus-1", null);
        Op setDefault = new Op("setDefaultPaymentMethod", HttpMethod.POST,
                ROUTE_BASE + "/payment-methods/" + m + "/default", "{\"customerRef\": \"cus-1\"}");
        Op removeMethod = new Op("removePaymentMethod", HttpMethod.DELETE,
                ROUTE_BASE + "/payment-methods/" + m + "?customerRef=cus-1", null);

        List<Op> all = List.of(requestCharge, capturePayment, cancelPayment, refundPayment, getPayment, getByCharge,
                listPayments, savePaymentMethod, listPaymentMethods, setDefault, removeMethod);
        // Con backoffice (payment:read, payment:refund): sin payment:charge ni payment-method:*.
        List<Op> notForBackoffice = List.of(requestCharge, capturePayment, cancelPayment, savePaymentMethod,
                listPaymentMethods, setDefault, removeMethod);
        List<Op> reads = List.of(getPayment, getByCharge, listPayments);

        // La ventana de observación se abre donde empieza lo que se prueba.
        purgeMessages(EVENTS);
        int chargesBefore = gatewayCallCount(GatewayCall.CHARGE);
        int capturesBefore = gatewayCallCount(GatewayCall.CAPTURE);
        int cancelsBefore = gatewayCallCount(GatewayCall.CANCEL);
        int refundsBefore = gatewayCallCount(GatewayCall.REFUND);
        int attachesBefore = gatewayCallCount(GatewayCall.ATTACH_METHOD);

        // 1. Sin credencial: las once responden 401.
        for (Op op : all) {
            Response response = exchange(op.method(), op.path(), op.body(), null);
            assertThat(response.status()).as("%s sin credencial: %s", op.name(), response.body()).isEqualTo(401);
        }

        // 2. Con la credencial de backoffice, las que exigen payment:charge o payment-method:* responden 403.
        for (Op op : notForBackoffice) {
            Response response = exchange(op.method(), op.path(), op.body(), backofficeToken());
            assertThat(response.status()).as("%s con backoffice: %s", op.name(), response.body()).isEqualTo(403);
        }

        // 3. Con checkout (sin payment:refund), refundPayment responde 403.
        Response refundAsCheckout = exchange(refundPayment.method(), refundPayment.path(), refundPayment.body(),
                checkoutToken());
        assertThat(refundAsCheckout.status()).as(refundAsCheckout.body()).isEqualTo(403);

        // 4. Con un token emitido para otra audiencia, las tres lecturas responden 403.
        for (Op op : reads) {
            Response response = exchange(op.method(), op.path(), op.body(), serviceCredential("test-m2m-bad-aud"));
            assertThat(response.status()).as("%s con otra audiencia: %s", op.name(), response.body()).isEqualTo(403);
        }

        // 5. Ninguna de esas peticiones produce efecto.
        assertThat(gatewayCallCount(GatewayCall.CHARGE)).isEqualTo(chargesBefore);
        assertThat(gatewayCallCount(GatewayCall.CAPTURE)).isEqualTo(capturesBefore);
        assertThat(gatewayCallCount(GatewayCall.CANCEL)).isEqualTo(cancelsBefore);
        assertThat(gatewayCallCount(GatewayCall.REFUND)).isEqualTo(refundsBefore);
        assertThat(attachCalls()).isEqualTo(attachesBefore);
        assertThat(publishedMessages(EVENTS, 10).trim()).as("paymentEvents sin mensajes").isEqualTo("[]");
        assertThat(statusOf(p)).isEqualTo("authorized");
    }
}
