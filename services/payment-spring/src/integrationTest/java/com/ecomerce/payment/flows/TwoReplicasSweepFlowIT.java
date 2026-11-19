package com.ecomerce.payment.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TwoReplicasSweepFlowIT extends PaymentFlowSupport {

    @BeforeAll
    void prepare() {
        resetState();
    }

    @Test
    @Order(1)
    @DisplayName("FL-CLU-001: con dos réplicas, cada cobro en duda se pregunta una vez")
    void eachPaymentInDoubtIsAskedOnce() {
        try {
            // Given: dos réplicas vivas contra el mismo almacén.
            int replicaPort = startReplica();
            assertThat(replicaPort).isPositive();

            // Cinco cobros en pending registrados por la pasarela sin contestar, con la marca envejecida.
            List<String> refs = List.of("ch-400", "ch-401", "ch-402", "ch-403", "ch-404");
            List<String> ids = new ArrayList<>();
            for (String ref : refs) {
                ids.add(seedPending(ref));
            }
            String control = seedPending("ch-405");
            for (String ref : refs) {
                gatewayReports(ref, "AUTHORIZED");
            }
            for (String id : ids) {
                ageAwaiting(id);
            }
            holdFromReconciliation("sweepPendingPayments", control);

            // When: pasan dos ciclos.
            runSweep();
            for (String id : ids) {
                awaitStatus(id, "authorized");
            }
            runSweep();
            holdsFor(Duration.ofSeconds(15), () -> statusQueries("ch-405") == 0);

            // Then: una consulta por cada uno de los cinco, ninguna por el de control.
            for (String ref : refs) {
                assertThat(statusQueries(ref)).as("consultas de %s", ref).isEqualTo(1);
            }
            assertThat(statusQueries("ch-405")).isZero();
            assertThat(statusOf(control)).isEqualTo("pending");
            for (int i = 0; i < refs.size(); i++) {
                String ref = refs.get(i);
                String gatewayPaymentId = jsonPath(readPayment(ids.get(i)), "$.gatewayPaymentId");
                assertThat(gatewayPaymentId).isNotBlank();
                assertEvent("PaymentAuthorized", ref, eventData(ids.get(i), ref, "cus-1", 10.0,
                        "gatewayPaymentId", gatewayPaymentId));
            }
        } finally {
            stopReplica();
        }
    }
}
