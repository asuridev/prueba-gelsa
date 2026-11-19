package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TemplateListingFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        // Given: P-SND; b-receipt, a-welcome y c-reminder; version solo en a-welcome y c-reminder.
        seedSender();
        seedTemplate("b-receipt", "Recibo");
        seedTemplate("a-welcome", "Bienvenida");
        seedTemplate("c-reminder", "Recordatorio");
        seedVersion("a-welcome", TPL_V1);
        seedVersion("c-reminder", TPL_V1);
    }

    private Response list(String query) {
        return get(TEMPLATES_PATH + query, tokenFor(EDITOR));
    }

    @Test
    @Order(1)
    @DisplayName("FL-TPL-030: listado de plantillas, filtro y paginacion")
    void listsTemplates() {
        // 1. Sin parametros.
        Response all = list("");
        assertStatus(all, 200);
        assertThat(fieldOf(all, "code")).containsExactly("a-welcome", "b-receipt", "c-reminder");
        Integer page = jsonPath(all, "$.page");
        Integer size = jsonPath(all, "$.size");
        Integer pages = jsonPath(all, "$.totalPages");
        assertThat(page).isZero();
        assertThat(size).isEqualTo(20);
        assertThat(totalElements(all)).isEqualTo(3);
        assertThat(pages).isEqualTo(1);

        // 2. Filtro por hasActiveVersion.
        Response unpublished = list("?hasActiveVersion=false");
        assertThat(fieldOf(unpublished, "code")).containsExactly("b-receipt");
        assertThat(totalElements(unpublished)).isEqualTo(1);

        // 3. Paginacion.
        Response firstPage = list("?size=2");
        assertThat(fieldOf(firstPage, "code")).containsExactly("a-welcome", "b-receipt");
        Integer firstPages = jsonPath(firstPage, "$.totalPages");
        assertThat(firstPages).isEqualTo(2);
        Response secondPage = list("?size=2&page=1");
        assertThat(fieldOf(secondPage, "code")).containsExactly("c-reminder");

        // 4. Fuera de rango.
        Response beyond = list("?page=5");
        assertStatus(beyond, 200);
        assertBody(beyond, pageJson(List.of(), 5, 20, 3, 1));

        // 5. Tope del tamano.
        Response huge = list("?size=500");
        Integer hugeSize = jsonPath(huge, "$.size");
        assertThat(hugeSize).isEqualTo(100);

        // 7. Orden pedido.
        Response descending = list("?sort=code,desc");
        assertThat(fieldOf(descending, "code")).containsExactly("c-reminder", "b-receipt", "a-welcome");

        // 8. Columnas u orden no permitidos.
        assertStatus(list("?sort=hasActiveVersion"), 400);
        assertStatus(list("?sort=description"), 400);
        assertStatus(list("?sort=code,sideways"), 400);

        // 6. Tras publicar en b-receipt ya no hay plantillas sin version activa.
        seedVersion("b-receipt", TPL_V1);
        Response none = list("?hasActiveVersion=false");
        assertStatus(none, 200);
        assertBody(none, pageJson(List.of(), 0, 20, 0, 0));
    }
}
