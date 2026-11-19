package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class SuppressionListingFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedSender();
        // Given: operator suprime a, b y c, en ese orden.
        suppress("a@example.com", "{\"reason\": \"manual\"}");
        suppress("b@example.com", "{\"reason\": \"manual\"}");
        suppress("c@example.com", "{\"reason\": \"manual\"}");
    }

    private Response list(String query) {
        return get(SUPPRESSIONS_PATH + query, tokenFor(OPERATOR));
    }

    @Test
    @Order(1)
    @DisplayName("FL-SUP-003: listado de supresiones, orden y paginacion")
    void listsSuppressions() {
        // 1. Mas reciente primero.
        Response all = list("");
        assertStatus(all, 200);
        assertThat(fieldOf(all, "address")).containsExactly("c@example.com", "b@example.com", "a@example.com");
        assertThat(totalElements(all)).isEqualTo(3);

        // 2. Paginacion.
        Response firstPage = list("?size=2");
        assertThat(fieldOf(firstPage, "address")).containsExactly("c@example.com", "b@example.com");
        Integer pages = jsonPath(firstPage, "$.totalPages");
        assertThat(pages).isEqualTo(2);
        assertThat(fieldOf(list("?size=2&page=1"), "address")).containsExactly("a@example.com");

        // 3. Fuera de rango y tope de tamano.
        Response beyond = list("?page=4");
        assertStatus(beyond, 200);
        assertThat(itemsOf(beyond)).isEmpty();
        assertThat(totalElements(beyond)).isEqualTo(3);
        Response huge = list("?size=500");
        Integer size = jsonPath(huge, "$.size");
        assertThat(size).isEqualTo(100);

        // 4. Orden pedido.
        assertThat(fieldOf(list("?sort=suppressedAt,asc"), "address"))
                .containsExactly("a@example.com", "b@example.com", "c@example.com");

        // 5. Ni la direccion ni el motivo se ofrecen para ordenar.
        assertStatus(list("?sort=address"), 400);
        assertStatus(list("?sort=reason"), 400);
    }
}
