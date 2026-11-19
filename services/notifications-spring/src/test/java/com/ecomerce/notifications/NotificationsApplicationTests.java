package com.ecomerce.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranque del contexto bajo el perfil `test`: sin contenedores y sin red. Es la
 * única comprobación de que TODOS los beans se construyen con esa configuración —
 * los escenarios corren con `local` contra infraestructura real y no lo cubren.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationsApplicationTests {

    @Test
    void contextLoads() {
    }
}
