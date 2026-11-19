package com.ecomerce.order.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class Ord013FlowIT extends OrderFlowSupport {

    /** a[0] = {a1} … a[24] = {a25}, en orden de creación. */
    private final List<String> a = new ArrayList<>();

    @BeforeAll
    void prepare() {
        resetState();
        stubCharge("authorized", PAY1, null, null);
    }

    @BeforeEach
    void given() {
        awaitPreconditions(() -> {
            seedCatalog();
            for (int i = 1; i <= 25; i++) {
                Response created = createdOrder(ana(), "k-ana-013-" + i, orderBody(TOKEN_OK, line(P1, 1)));
                a.add(idOf(created));
            }
            createdOrder(luis(), "k-luis-013", orderBody(TOKEN_OK, line(P1, 1)));
        });
    }

    private List<String> idsOf(Response response) {
        List<String> ids = jsonPath(response, "$.items[*].id");
        return ids;
    }

    @Test
    @Order(1)
    @DisplayName("FL-ORD-013: listado paginado de mis pedidos")
    void paginatedListing() {
        Response page0 = get(ORDERS, ana());
        assertThat(page0.status()).as(page0.body()).isEqualTo(200);
        Integer page = jsonPath(page0, "$.page");
        Integer size = jsonPath(page0, "$.size");
        Integer total = jsonPath(page0, "$.totalElements");
        Integer pages = jsonPath(page0, "$.totalPages");
        assertThat(page).isZero();
        assertThat(size).isEqualTo(20);
        assertThat(total).isEqualTo(25);
        assertThat(pages).isEqualTo(2);
        List<String> ids = idsOf(page0);
        assertThat(ids).hasSize(20);
        assertThat(ids.get(0)).isEqualTo(a.get(24));
        assertThat(ids.get(19)).isEqualTo(a.get(5));
        List<Map<String, Object>> items = jsonPath(page0, "$.items");
        for (Map<String, Object> item : items) {
            assertThat(item.keySet()).containsExactlyInAnyOrder("id", "customerRef", "customerEmail", "status",
                    "currency", "totalAmount", "lineCount", "paymentId", "customerAction", "failureReason",
                    "createdAt", "updatedAt", "lines");
            assertThat(item.get("customerRef")).isEqualTo("ana");
        }

        Response page1 = get(ORDERS + "?page=1&size=20", ana());
        assertThat(page1.status()).isEqualTo(200);
        Integer page1Number = jsonPath(page1, "$.page");
        Integer page1Total = jsonPath(page1, "$.totalElements");
        Integer page1Pages = jsonPath(page1, "$.totalPages");
        assertThat(page1Number).isEqualTo(1);
        assertThat(page1Total).isEqualTo(25);
        assertThat(page1Pages).isEqualTo(2);
        assertThat(idsOf(page1)).containsExactly(a.get(4), a.get(3), a.get(2), a.get(1), a.get(0));

        Response outOfRange = get(ORDERS + "?page=5&size=20", ana());
        assertThat(outOfRange.status()).isEqualTo(200);
        assertBody(outOfRange, "{\"items\":[],\"page\":5,\"size\":20,\"totalElements\":0,\"totalPages\":0}");

        Response big = get(ORDERS + "?size=500", ana());
        assertThat(big.status()).isEqualTo(200);
        Integer bigSize = jsonPath(big, "$.size");
        assertThat(bigSize).isEqualTo(100);
        assertThat(idsOf(big)).hasSize(25);

        Response asc = get(ORDERS + "?sort=createdAt,asc", ana());
        assertThat(asc.status()).isEqualTo(200);
        List<String> ascIds = idsOf(asc);
        assertThat(ascIds.get(0)).isEqualTo(a.get(0));
        assertThat(ascIds.get(19)).isEqualTo(a.get(19));

        assertThat(get(ORDERS + "?sort=totalAmount,desc", ana()).status()).isEqualTo(400);

        Response paid = get(ORDERS + "?status=paid", ana());
        assertThat(paid.status()).isEqualTo(200);
        Integer paidTotal = jsonPath(paid, "$.totalElements");
        assertThat(paidTotal).isZero();
        assertThat(idsOf(paid)).isEmpty();

        // Caso borde: estado desconocido.
        assertThat(get(ORDERS + "?status=desconocido", ana()).status()).isEqualTo(400);
    }
}
