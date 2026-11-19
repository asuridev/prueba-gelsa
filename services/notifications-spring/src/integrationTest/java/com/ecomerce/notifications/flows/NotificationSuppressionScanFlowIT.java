package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class NotificationSuppressionScanFlowIT extends NotificationsFlowBase {

    /** Muestras por forma: el minimo descarta el ruido de los sondeadores de fondo (relay, cronometro). */
    private static final int SAMPLES = 5;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    private static List<String> recipients(int count) {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(String.format(Locale.ROOT, "u%02d@example.com", i));
        }
        return list;
    }

    /**
     * Consultas que cuesta aceptar una peticion con esos destinatarios. Se mide con
     * {@code queryExecutions()} y no con {@code queryCount()} porque el When ESCRIBE: los INSERT
     * de los destinatarios no son la comprobacion de supresiones.
     */
    private long acceptanceQueries(String key, List<String> to) {
        String body = requestJson(key, "invoice-issued", to, r1Variables());
        long before = queryExecutions();
        Response response = requestAs("billing", body);
        long after = queryExecutions();
        assertStatus(response, 202);
        return after - before;
    }

    @Test
    @Order(1)
    @DisplayName("FL-NTF-040: el coste de comprobar supresiones no crece con los destinatarios")
    void suppressionCheckCoversAllRecipients() {
        // 1. Un destinatario y veinte distintos: las dos se aceptan (y calientan planes y caches).
        assertStatus(requestAs("billing", r1To("one-0001", "solo@example.com")), 202);
        assertStatus(requestAs("billing", requestJson("twenty-0001", "invoice-issued", recipients(20), r1Variables())),
                202);

        // El coste: veinte destinatarios no cuestan mas consultas que uno. Se compara la FORMA
        // (el salto), no un numero absoluto, midiendo ejecuciones ya calientes y alternadas.
        long small = Long.MAX_VALUE;
        long large = Long.MAX_VALUE;
        for (int i = 1; i <= SAMPLES; i++) {
            String suffix = String.format(Locale.ROOT, "%04d", i);
            small = Math.min(small, acceptanceQueries("cost-one-" + suffix, List.of("solo@example.com")));
            large = Math.min(large, acceptanceQueries("cost-twenty-" + suffix, recipients(20)));
        }
        assertThat(large).as("consultas con 20 destinatarios (%d) frente a 1 (%d)", large, small)
                .isLessThanOrEqualTo(small + 2);

        // 2. Suprimida la direccion en la posicion 20, la misma peticion con otra clave se rechaza:
        //    se comprueban todos los destinatarios, tambien el ultimo.
        suppress("u20@example.com", "{\"reason\": \"manual\"}");
        assertError(requestAs("billing", requestJson("twenty-0002", "invoice-issued", recipients(20), r1Variables())),
                422, "RECIPIENT_SUPPRESSED");
    }
}
