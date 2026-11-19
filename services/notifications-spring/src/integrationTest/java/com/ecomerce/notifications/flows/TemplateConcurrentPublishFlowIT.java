package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class TemplateConcurrentPublishFlowIT extends NotificationsFlowBase {

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
    }

    @Test
    @Order(1)
    @DisplayName("FL-TPL-011: dos publicaciones a la vez no dejan dos versiones activas")
    void concurrentPublishesLeaveOneActive() {
        String contentA = withField(TPL_V1, "subject", "Version A {{invoiceNumber}}");
        String contentB = withField(TPL_V1, "subject", "Version B {{invoiceNumber}}");
        List<Response> results = race(List.of(
                () -> publish("invoice-issued", contentA),
                () -> publish("invoice-issued", contentB)));

        // 1. Desenlaces admisibles: 200+200 con numeros 2 y 3, o 200+409 CONCURRENT_MODIFICATION.
        List<Integer> numbers = new ArrayList<>();
        for (Response result : results) {
            if (result.status() == 200) {
                Integer number = jsonPath(result, "$.versionNumber");
                numbers.add(number);
            } else {
                assertError(result, 409, "CONCURRENT_MODIFICATION");
            }
        }
        assertThat(numbers).as("al menos una publicacion gana").isNotEmpty();
        if (numbers.size() == 2) {
            assertThat(numbers).containsExactlyInAnyOrder(2, 3);
        }

        // 2. Exactamente una version active, la de mayor versionNumber, sin numeros repetidos.
        Response list = get(TEMPLATES_PATH + "/invoice-issued/versions", tokenFor(EDITOR));
        assertStatus(list, 200);
        List<Integer> versionNumbers = JsonPath.read(list.body(), "$.items[*].versionNumber");
        List<String> statuses = JsonPath.read(list.body(), "$.items[*].status");
        assertThat(versionNumbers).doesNotHaveDuplicates();
        assertThat(statuses.stream().filter("active"::equals).count()).isEqualTo(1);
        int highest = versionNumbers.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int activeIndex = statuses.indexOf("active");
        assertThat(versionNumbers.get(activeIndex)).isEqualTo(highest);

        // 3. La plantilla sigue publicada.
        Response template = get(TEMPLATES_PATH + "/invoice-issued", tokenFor(EDITOR));
        Boolean hasActive = jsonPath(template, "$.hasActiveVersion");
        assertThat(hasActive).isTrue();
    }
}
