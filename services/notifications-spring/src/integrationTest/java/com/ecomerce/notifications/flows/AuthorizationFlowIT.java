package com.ecomerce.notifications.flows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class AuthorizationFlowIT extends NotificationsFlowBase {

    private String m1;

    @BeforeAll
    void prepare() {
        resetState();
        seedP_TPL();
        // Given: m1 pedido por billing; pepe@example.com suprimida.
        m1 = requestAccepted("billing", r1("inv-2026-0001"));
        suppress("pepe@example.com", "{\"reason\": \"manual\"}");
    }

    /** Una operacion protegida y los roles de usuario (o credencial de maquina) que NO tienen permiso. */
    private record Operation(String name, HttpMethod method, String path, String body, List<String> forbiddenRoles,
            String forbiddenClient) {
    }

    private List<Operation> operations() {
        String version = "{\"subject\": \"S {{a}}\", \"htmlBody\": \"<p>{{a}}</p>\", \"textBody\": \"{{a}}\","
                + " \"variables\": [{\"name\": \"a\", \"description\": \"a\", \"required\": false}]}";
        String tpl = TEMPLATES_PATH + "/invoice-issued";
        return List.of(
                new Operation("configureSenderSettings", HttpMethod.PUT, SENDER_PATH, P_SND,
                        List.of(EDITOR, OPERATOR, AUDITOR), null),
                new Operation("getSenderSettings", HttpMethod.GET, SENDER_PATH, null, List.of(EDITOR, OPERATOR), null),
                new Operation("createTemplate", HttpMethod.POST, TEMPLATES_PATH,
                        "{\"code\": \"authz-probe\", \"name\": \"Probe\"}", List.of(OPERATOR, AUDITOR), null),
                new Operation("publishTemplateVersion", HttpMethod.POST, tpl + "/versions", version,
                        List.of(OPERATOR, AUDITOR), null),
                new Operation("activateTemplateVersion", HttpMethod.POST, tpl + "/versions/1/activate", "{}",
                        List.of(OPERATOR, AUDITOR), null),
                new Operation("retireTemplate", HttpMethod.POST, tpl + "/retire", "{}", List.of(OPERATOR, AUDITOR),
                        null),
                new Operation("getTemplate", HttpMethod.GET, tpl, null, List.of(), "billing"),
                new Operation("listTemplates", HttpMethod.GET, TEMPLATES_PATH, null, List.of(), "billing"),
                new Operation("getTemplateVersion", HttpMethod.GET, tpl + "/versions/1", null, List.of(), "billing"),
                new Operation("listTemplateVersions", HttpMethod.GET, tpl + "/versions", null, List.of(), "billing"),
                new Operation("getMessage", HttpMethod.GET, MESSAGES_PATH + "/" + m1, null, List.of(EDITOR), null),
                new Operation("listMessages", HttpMethod.GET, MESSAGES_PATH, null, List.of(EDITOR), null),
                new Operation("suppressAddress", HttpMethod.PUT, SUPPRESSIONS_PATH + "/pepe@example.com",
                        "{\"reason\": \"manual\"}", List.of(EDITOR, AUDITOR), null),
                new Operation("releaseAddress", HttpMethod.POST, SUPPRESSIONS_PATH + "/pepe@example.com/release", "{}",
                        List.of(EDITOR, AUDITOR), null),
                new Operation("getSuppressedAddress", HttpMethod.GET, SUPPRESSIONS_PATH + "/pepe@example.com", null,
                        List.of(EDITOR), null),
                new Operation("listSuppressedAddresses", HttpMethod.GET, SUPPRESSIONS_PATH, null, List.of(EDITOR),
                        null),
                new Operation("purgeMessagePersonalData", HttpMethod.POST, ROUTE_BASE + "/maintenance/purge-personal-data",
                        "{}", List.of(OPERATOR, AUDITOR), null),
                // Superficie servidor a servidor: solo la comprobacion sin credencial.
                new Operation("requestNotification", HttpMethod.POST, NOTIFICATIONS_PATH, r1("authz-0001"), List.of(),
                        null),
                new Operation("findMessageByIdempotencyKey", HttpMethod.GET, NOTIFICATIONS_PATH + "/inv-2026-0001",
                        null, List.of(), null),
                new Operation("reportEmailBounce", HttpMethod.POST, MESSAGES_PATH + "/" + m1 + "/bounces",
                        "{\"recipient\": \"ana@example.com\", \"bounceType\": \"transient\"}", List.of(), null));
    }

    @Test
    @Order(1)
    @DisplayName("FL-SEC-003: autorizacion por operacion")
    void authorizationPerOperation() {
        for (Operation operation : operations()) {
            // Sin credencial: 401 UNAUTHENTICATED.
            Response anonymous = exchange(operation.method(), operation.path(), operation.body(), null);
            assertThat(anonymous.status()).as(operation.name() + " sin credencial: " + anonymous.body()).isEqualTo(401);
            String code = jsonPath(anonymous, "$.code");
            assertThat(code).as(operation.name()).isEqualTo("UNAUTHENTICATED");

            // Sin permiso: 403 ACCESS_DENIED.
            for (String role : operation.forbiddenRoles()) {
                Response denied = exchange(operation.method(), operation.path(), operation.body(), tokenFor(role));
                assertThat(denied.status()).as(operation.name() + " con " + role + ": " + denied.body()).isEqualTo(403);
                String deniedCode = jsonPath(denied, "$.code");
                assertThat(deniedCode).as(operation.name() + " con " + role).isEqualTo("ACCESS_DENIED");
            }
            if (operation.forbiddenClient() != null) {
                Response denied = exchange(operation.method(), operation.path(), operation.body(),
                        serviceCredential(operation.forbiddenClient()));
                assertThat(denied.status()).as(operation.name() + " con la credencial de " + operation.forbiddenClient()
                        + ": " + denied.body()).isEqualTo(403);
                String deniedCode = jsonPath(denied, "$.code");
                assertThat(deniedCode).isEqualTo("ACCESS_DENIED");
            }
        }

        // 9. Con permiso: auditor lee; operator suprime y libera.
        assertStatus(get(SENDER_PATH, tokenFor(AUDITOR)), 200);
        assertStatus(get(TEMPLATES_PATH + "/invoice-issued", tokenFor(AUDITOR)), 200);
        assertStatus(get(MESSAGES_PATH + "/" + m1, tokenFor(AUDITOR)), 200);
        assertStatus(get(SUPPRESSIONS_PATH, tokenFor(AUDITOR)), 200);
        assertStatus(put(SUPPRESSIONS_PATH + "/pepe@example.com", "{\"reason\": \"manual\"}", tokenFor(OPERATOR)), 200);
        assertStatus(post(SUPPRESSIONS_PATH + "/pepe@example.com/release", "{}", tokenFor(OPERATOR)), 200);
    }
}
