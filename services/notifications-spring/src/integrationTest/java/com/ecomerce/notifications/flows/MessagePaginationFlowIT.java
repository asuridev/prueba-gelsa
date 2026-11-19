package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class MessagePaginationFlowIT extends NotificationsFlowBase {

    private String k1;
    private String k2;
    private String k3;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: billing pide 3 notificaciones, en orden.
        k1 = requestAccepted("billing", r1("page-0001"));
        k2 = requestAccepted("billing", r1("page-0002"));
        k3 = requestAccepted("billing", r1("page-0003"));
    }

    private Response list(String query) {
        return get(MESSAGES_PATH + query, tokenFor(ADMIN));
    }

    @Test
    @Order(1)
    @DisplayName("FL-MSG-002: paginacion del listado de correos")
    void paginatesMessages() {
        // 1. Paginas de 2: la segunda trae el mas antiguo.
        Response firstPage = list("?size=2");
        assertStatus(firstPage, 200);
        assertThat(idsOf(firstPage)).containsExactly(k3, k2);
        Integer pages = jsonPath(firstPage, "$.totalPages");
        assertThat(totalElements(firstPage)).isEqualTo(3);
        assertThat(pages).isEqualTo(2);
        assertThat(idsOf(list("?size=2&page=1"))).containsExactly(k1);

        // 2. Fuera de rango y tope de tamano.
        assertThat(itemsOf(list("?page=9"))).isEmpty();
        Response huge = list("?size=500");
        Integer size = jsonPath(huge, "$.size");
        assertThat(size).isEqualTo(100);

        // 3. Filtro sin resultados.
        Response none = list("?templateCode=no-such-tpl");
        assertStatus(none, 200);
        Integer nonePages = jsonPath(none, "$.totalPages");
        assertThat(itemsOf(none)).isEmpty();
        assertThat(totalElements(none)).isZero();
        assertThat(nonePages).isZero();

        // 4. Orden pedido.
        assertThat(idsOf(list("?sort=requestedAt,asc"))).containsExactly(k1, k2, k3);

        // 5. Columnas no ordenables.
        assertStatus(list("?sort=status"), 400);
        assertStatus(list("?sort=requestedBy"), 400);
        assertStatus(list("?sort=recipients"), 400);
    }
}
