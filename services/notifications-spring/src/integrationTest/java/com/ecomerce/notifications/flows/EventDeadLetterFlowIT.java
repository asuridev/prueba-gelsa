package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class EventDeadLetterFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-EVT-002: lo que no se puede aceptar va a la deadLetter")
    void unacceptableRequestsGoToDeadLetter() {
        // When: (a) plantilla inexistente, (b) fuente desconocida, (c) fuente que no es un llamante.
        deliverNotificationRequested(UUID.randomUUID().toString(), "billing",
                requestJson("evt-0001", "no-existe", java.util.List.of("ana@example.com"), r1Variables()));
        deliverNotificationRequested(UUID.randomUUID().toString(), "unknown-system", r1("evt-0002"));
        deliverNotificationRequested(UUID.randomUUID().toString(), "mail-provider", r1("evt-0003"));

        // 1. Los tres acaban en la deadLetter de la suscripcion.
        await(Duration.ofSeconds(90), () -> {
            String dead = deadLetterMessages("NotificationRequested", 20);
            return dead.contains("evt-0001") && dead.contains("evt-0002") && dead.contains("evt-0003");
        });

        // 2. El emisor reconcilia: la peticion (a) no se acepto.
        assertError(findByKey("billing", "evt-0001"), 404, "MESSAGE_NOT_FOUND");

        // 3. Ningun mensaje y ningun correo.
        Response all = get(MESSAGES_PATH, tokenFor(ADMIN));
        assertThat(totalElements(all)).isZero();
        assertNoMailTo("ana@example.com");
    }
}
