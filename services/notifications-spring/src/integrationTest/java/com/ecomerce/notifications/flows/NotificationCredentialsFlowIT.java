package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationCredentialsFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pidio R1 -> m1.
        requestAccepted("billing", r1("inv-2026-0001"));
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-030: la superficie servidor a servidor y sus credenciales")
    void machineSurfaceAndCredentials() {
        // 1. Sin credencial.
        assertError(post(NOTIFICATIONS_PATH, r1("inv-2026-0002")), 401, "UNAUTHENTICATED");

        // 2. dispatch-only solo puede pedir.
        Response dispatch = requestAs("dispatch-only", r1("dsp-0001"));
        assertStatus(dispatch, 202);
        String requestedBy = jsonPath(dispatch, "$.requestedBy");
        assertThat(requestedBy).isEqualTo("dispatch-only");

        // 3. ... y no tiene notification:read.
        assertError(findByKey("dispatch-only", "dsp-0001"), 403, "ACCESS_DENIED");

        // 4. mail-provider no tiene notification:send.
        assertError(requestAs("mail-provider", r1("inv-2026-0003")), 403, "ACCESS_DENIED");

        // 5. Credencial de otra audiencia (cliente de prueba con los scopes correctos y audiencia ajena).
        assertError(requestAs("test-m2m-bad-aud", r1("inv-2026-0004")), 403, "ACCESS_DENIED");

        // 6. Un usuario (admin) no tiene el scope.
        assertError(post(NOTIFICATIONS_PATH, r1("inv-2026-0005"), tokenFor(ADMIN)), 403, "ACCESS_DENIED");

        // 7. shipping solo encuentra mensajes propios.
        assertError(findByKey("shipping", "inv-2026-0001"), 404, "MESSAGE_NOT_FOUND");
    }
}
