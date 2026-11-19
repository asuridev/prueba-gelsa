package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class ThreeDsTimeoutFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-REC-003: el cobro que espera al cliente demasiado tiempo")
    void paymentWaitingForTheCustomerTooLong() {
        // Given: tres cobros en actionRequired que la pasarela sigue dando por pendientes del cliente.
        String p320 = seedActionRequired("ch-320");
        String p321 = seedActionRequired("ch-321");
        String p322 = seedActionRequired("ch-322");
        Map<String, Object> before321 = JsonPath.read(readPayment(p321).body(), "$");

        gatewayReports("ch-320", "ACTION_REQUIRED");
        gatewayReports("ch-321", "ACTION_REQUIRED");
        gatewayReports("ch-322", "ACTION_REQUIRED");
        gatewayCancels("ch-320");
        // ch-322: la pasarela contesta a la anulación diciendo que el cliente falló la autenticación.
        String id322 = gatewayIdFor("ch-322");
        stubFor("POST", "/v1/payment_intents/" + id322 + "/cancel", 200,
                "{\"id\": \"" + id322 + "\", \"object\": \"payment_intent\", \"currency\": \"eur\", "
                        + "\"status\": \"requires_payment_method\", "
                        + "\"metadata\": {\"keel_reference\": \"ch-322\"}, \"client_secret\": \"" + id322
                        + "_secret_test\", \"last_payment_error\": {\"code\": \"card_declined\", "
                        + "\"decline_code\": \"authentication_required\"}}");

        // ch-320 y ch-322 con los dos relojes envejecidos; ch-321 solo el de espera.
        ageActionRequired(p320);
        ageActionRequired(p322);
        ageAwaiting(p320);
        ageAwaiting(p321);
        ageAwaiting(p322);

        // When: pasa un ciclo del barrido.
        runSweep();
        awaitStatus(p320, "canceled");
        awaitStatus(p322, "failed");
        await(Duration.ofSeconds(60), () -> statusQueries("ch-321") >= 1);

        // Then: ch-320 se anula por el plazo del 3DS.
        assertThat(cancelCalls("ch-320")).isGreaterThanOrEqualTo(1);
        assertPayment(readPayment(p320), 200, payment("id", p320, "chargeRequestId", "ch-320", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "canceled", "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentCanceled", "ch-320", eventData(p320, "ch-320", "cus-1", 10.0, "requested", true));

        // ch-321 se consulta pero no se anula: las consultas del barrido no cuentan para el plazo.
        assertThat(cancelCalls("ch-321")).isZero();
        assertPayment(readPayment(p321), 200, payment("id", p321, "chargeRequestId", "ch-321", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "actionRequired",
                "gatewayPaymentId", before321.get("gatewayPaymentId"), "awaitingSince", NOT_NULL,
                "actionRequiredSince", before321.get("actionRequiredSince"),
                "customerAction", before321.get("customerAction")));

        // ch-322 termina fallido por la autenticación.
        assertPayment(readPayment(p322), 200, payment("id", p322, "chargeRequestId", "ch-322", "customerRef", "cus-1",
                "amount", 10.0, "currency", "EUR", "status", "failed", "failureReason", "authenticationFailed",
                "gatewayPaymentId", NOT_NULL));
        assertEvent("PaymentFailed", "ch-322", eventData(p322, "ch-322", "cus-1", 10.0,
                "failureReason", "authenticationFailed"));
    }
}
