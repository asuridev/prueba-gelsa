package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationRetiredTemplateFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pidio R1 -> m1.
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-020: la repeticion se honra aunque la plantilla se haya retirado")
    void repetitionSurvivesRetirement() {
        // When: se retira la plantilla y billing repite R1.
        Response retired = post(TEMPLATES_PATH + "/invoice-issued/retire", "{}", tokenFor(EDITOR));
        assertStatus(retired, 200);
        Response repeat = requestAs("billing", r1("inv-2026-0001"));

        // 1. 202 con m1, no TEMPLATE_NOT_PUBLISHED.
        assertStatus(repeat, 202);
        String repeatedId = jsonPath(repeat, "$.id");
        assertThat(repeatedId).isEqualTo(m1);

        // 2. Una peticion nueva si se rechaza.
        assertError(requestAs("billing", r1("inv-2026-0009")), 409, "TEMPLATE_NOT_PUBLISHED");

        // 3. m1 se envia igual: congelo su version.
        awaitStatus(m1, "sent");
        awaitMailOfMessage("ana@example.com", m1);
    }
}
