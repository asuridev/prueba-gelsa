package com.ecomerce.payment.infrastructure.payment.stripe;

import com.ecomerce.payment.application.port.out.PaymentGateway;
import com.ecomerce.payment.domain.enums.FailureReason;
import com.ecomerce.payment.domain.payment.ChargeRequest;
import com.ecomerce.payment.domain.payment.GatewayOutcome;
import com.ecomerce.payment.domain.payment.GatewayStatus;
import com.ecomerce.payment.domain.payment.PaymentGatewayUnavailableException;
import com.ecomerce.payment.domain.payment.PaymentMethodNotAcceptedException;
import com.ecomerce.payment.domain.payment.PaymentSource;
import com.ecomerce.payment.domain.payment.SavedPaymentMethod;
import com.ecomerce.payment.infrastructure.payment.MoneyAmounts;
import com.ecomerce.payment.infrastructure.payment.PaymentGatewayProperties;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * La pasarela de pago sobre Stripe (Payment Intents), por HTTP plano.
 *
 * <p>Lo que lleva dentro y no puede faltar:
 * <ul>
 *   <li>la clave de idempotencia sale de la referencia de negocio y la acción
 *       ({@code <referencia>:authorize}…), nunca de un aleatorio: un reintento repite la clave;</li>
 *   <li>un 5xx o un timeout NO se reintenta: lanza {@link PaymentGatewayUnavailableException} y la
 *       acción queda en duda para el barrido (Stripe guarda también los 500 con su clave);</li>
 *   <li>el importe se convierte a la unidad menor sin redondear;</li>
 *   <li>los rechazos se traducen al vocabulario neutro de FailureReason.</li>
 * </ul>
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    /** La clave de metadata con la que viaja la referencia del cobro; es lo que permite buscarlo. */
    static final String REFERENCE_KEY = "keel_reference";

    private final RestClient http;
    private final PaymentGatewayProperties properties;
    private final ObjectMapper mapper;

    public StripePaymentGateway(RestClient paymentGatewayRestClient, PaymentGatewayProperties properties, ObjectMapper mapper) {
        this.http = paymentGatewayRestClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public GatewayOutcome authorize(ChargeRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(MoneyAmounts.toMinorUnits(request.amount(), request.currency())));
        form.add("currency", request.currency().toLowerCase(Locale.ROOT));
        form.add("capture_method", "manual");
        form.add("confirm", "true");
        form.add("payment_method_types[]", "card");
        form.add("metadata[" + REFERENCE_KEY + "]", request.reference());
        if (request.source().kind() == PaymentSource.Kind.SAVED) {
            // La referencia guardada es "customer|payment_method" (savePaymentMethod).
            String[] saved = request.source().value().split("\\|", 2);
            form.add("customer", saved[0]);
            form.add("payment_method", saved[1]);
            form.add("off_session", "true");
        } else {
            form.add("payment_method", request.source().value());
        }
        return post("/v1/payment_intents", form, idempotencyKey(request.reference(), "authorize"), request.reference());
    }

    @Override
    public GatewayOutcome capture(String reference, String gatewayPaymentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        return followUp("/v1/payment_intents/" + gatewayPaymentId + "/capture", form, reference, gatewayPaymentId, "capture");
    }

    @Override
    public GatewayOutcome voidAuthorization(String reference, String gatewayPaymentId) {
        return followUp("/v1/payment_intents/" + gatewayPaymentId + "/cancel", new LinkedMultiValueMap<>(), reference,
                gatewayPaymentId, "void");
    }

    @Override
    public GatewayOutcome refund(String reference, String gatewayPaymentId, BigDecimal amount, String currency) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", gatewayPaymentId);
        if (amount != null) {
            form.add("amount", Long.toString(MoneyAmounts.toMinorUnits(amount, currency)));
        }
        JsonNode refund;
        try {
            refund = json(http.post().uri("/v1/refunds")
                    .headers(headers -> credentials(headers, idempotencyKey(reference, "refund")))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is5xxServerError()) {
                throw unavailable("refund", rejected);
            }
            // Rechazada: el cobro sigue capturado.
            return GatewayOutcome.of(GatewayStatus.CAPTURED, reference, gatewayPaymentId);
        } catch (RestClientException noAnswer) {
            throw unavailable("refund", noAnswer);
        }
        return switch (refund.path("status").asString()) {
            case "succeeded" -> GatewayOutcome.refunded(reference, gatewayPaymentId,
                    MoneyAmounts.fromMinorUnits(refund.path("amount").asLong(), refund.path("currency").asString().toUpperCase(Locale.ROOT)));
            case "pending", "requires_action" -> GatewayOutcome.of(GatewayStatus.PENDING, reference, gatewayPaymentId);
            default -> GatewayOutcome.of(GatewayStatus.CAPTURED, reference, gatewayPaymentId);
        };
    }

    @Override
    public GatewayOutcome status(String reference, String gatewayPaymentId) {
        try {
            if (gatewayPaymentId != null) {
                return outcomeOf(json(http.get().uri("/v1/payment_intents/{id}?expand[]=latest_charge", gatewayPaymentId)
                        .headers(headers -> credentials(headers, null))
                        .retrieve()
                        .body(String.class)), reference);
            }
            // Sin id: la pasarela no llegó a contestar. Se busca por la referencia que se le mandó en
            // metadata. La búsqueda de Stripe es eventualmente consistente (puede tardar un minuto en
            // ver un PaymentIntent recién creado): por eso el barrido solo mira lo que lleva más del
            // umbral esperando, y un NOT_FOUND aquí significa que no llegó.
            JsonNode found = json(http.get()
                    .uri(uri -> uri.path("/v1/payment_intents/search")
                            .queryParam("query", "metadata['" + REFERENCE_KEY + "']:'" + reference + "'")
                            .queryParam("expand[]", "data.latest_charge")
                            .build())
                    .headers(headers -> credentials(headers, null))
                    .retrieve()
                    .body(String.class));
            JsonNode first = found.path("data").path(0);
            return first.isMissingNode() ? GatewayOutcome.notFound(reference) : outcomeOf(first, reference);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                return GatewayOutcome.notFound(reference);
            }
            throw unavailable("status", error);
        } catch (RestClientException noAnswer) {
            throw unavailable("status", noAnswer);
        }
    }

    @Override
    public SavedPaymentMethod savePaymentMethod(String token, String payerReference) {
        try {
            MultiValueMap<String, String> customerForm = new LinkedMultiValueMap<>();
            customerForm.add("metadata[keel_payer]", payerReference);
            JsonNode customer = json(http.post().uri("/v1/customers")
                    .headers(headers -> credentials(headers, "save:" + token + ":customer"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(customerForm)
                    .retrieve()
                    .body(String.class));
            String customerId = customer.path("id").asString();
            MultiValueMap<String, String> attachForm = new LinkedMultiValueMap<>();
            attachForm.add("customer", customerId);
            JsonNode attached = json(http.post().uri("/v1/payment_methods/{pm}/attach", token)
                    .headers(headers -> credentials(headers, "save:" + token + ":attach"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(attachForm)
                    .retrieve()
                    .body(String.class));
            JsonNode card = attached.path("card");
            if (!card.isObject()) {
                // El adjuntado no describió la tarjeta: se le pregunta a la pasarela por el medio.
                card = json(http.get().uri("/v1/payment_methods/{pm}", token)
                        .headers(headers -> credentials(headers, null))
                        .retrieve()
                        .body(String.class)).path("card");
            }
            if (!card.isObject()) {
                throw new PaymentMethodNotAcceptedException("La pasarela no describe una tarjeta para el medio", false, null);
            }
            return new SavedPaymentMethod(customerId + "|" + token, card.path("brand").asString("unknown"),
                    card.path("last4").asString(""), card.path("exp_month").asInt(), card.path("exp_year").asInt());
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is5xxServerError()) {
                throw unavailable("savePaymentMethod", rejected);
            }
            JsonNode error = json(rejected.getResponseBodyAsString()).path("error");
            String code = error.path("decline_code").asString(error.path("code").asString(""));
            throw new PaymentMethodNotAcceptedException("La pasarela no acepta el medio de pago: " + rejected.getStatusText(),
                    "authentication_required".equals(code), rejected);
        } catch (RestClientException noAnswer) {
            throw unavailable("savePaymentMethod", noAnswer);
        }
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────

    private GatewayOutcome post(String path, MultiValueMap<String, String> form, String idempotencyKey, String reference) {
        try {
            return outcomeOf(json(http.post().uri(path)
                    .headers(headers -> credentials(headers, idempotencyKey))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class)), reference);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().is5xxServerError()) {
                throw unavailable(path, error);
            }
            return declined(error, reference);
        } catch (RestClientException noAnswer) {
            throw unavailable(path, noAnswer);
        }
    }

    /** Captura y anulación: si Stripe la rechaza por el estado del cobro, se devuelve el estado real. */
    private GatewayOutcome followUp(String path, MultiValueMap<String, String> form, String reference, String gatewayPaymentId,
            String action) {
        try {
            return outcomeOf(json(http.post().uri(path)
                    .headers(headers -> credentials(headers, idempotencyKey(reference, action)))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class)), reference);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().is5xxServerError()) {
                throw unavailable(action, error);
            }
            // La autorización caducó: es la respuesta de la pasarela y no hace falta preguntarle.
            // Se busca en el texto y no se parsea: un 4xx con un cuerpo que no es JSON no puede
            // convertir una respuesta en una excepción.
            if (error.getResponseBodyAsString().contains("charge_expired_for_capture")) {
                return GatewayOutcome.of(GatewayStatus.CANCELED, reference, gatewayPaymentId);
            }
            // payment_intent_unexpected_state y compañía (ya se capturó…): el estado real se consulta.
            return status(reference, gatewayPaymentId);
        } catch (RestClientException noAnswer) {
            throw unavailable(action, noAnswer);
        }
    }

    private void credentials(HttpHeaders headers, String idempotencyKey) {
        headers.setBearerAuth(properties.apiKey());
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
    }

    /** La clave de una acción sobre un cobro: la misma en cada reintento y por las dos puertas. */
    static String idempotencyKey(String reference, String action) {
        return reference + ":" + action;
    }

    // ─── Traducción ──────────────────────────────────────────────────────────

    private GatewayOutcome outcomeOf(JsonNode intent, String reference) {
        String id = intent.path("id").asString(null);
        String ref = intent.path("metadata").path(REFERENCE_KEY).asString(reference);
        JsonNode charge = intent.path("latest_charge");
        if (charge.isObject() && charge.path("amount_refunded").asLong() > 0) {
            return GatewayOutcome.refunded(ref, id, MoneyAmounts.fromMinorUnits(charge.path("amount_refunded").asLong(),
                    charge.path("currency").asString().toUpperCase(Locale.ROOT)));
        }
        return switch (intent.path("status").asString()) {
            case "requires_capture" -> GatewayOutcome.of(GatewayStatus.AUTHORIZED, ref, id);
            case "succeeded" -> GatewayOutcome.of(GatewayStatus.CAPTURED, ref, id);
            case "requires_action" -> GatewayOutcome.actionRequired(ref, id, customerAction(intent));
            case "canceled" -> GatewayOutcome.of(GatewayStatus.CANCELED, ref, id);
            case "requires_payment_method" -> {
                JsonNode error = intent.path("last_payment_error");
                yield error.isMissingNode() || error.isNull()
                        ? GatewayOutcome.of(GatewayStatus.PENDING, ref, id)
                        : GatewayOutcome.failed(ref, id, reasonOf(error));
            }
            default -> GatewayOutcome.of(GatewayStatus.PENDING, ref, id);
        };
    }

    /** Un 4xx al pedir el cobro: un rechazo del emisor (402) o un medio que no sirve (400). */
    private GatewayOutcome declined(RestClientResponseException rejected, String reference) {
        JsonNode error = json(rejected.getResponseBodyAsString()).path("error");
        JsonNode intent = error.path("payment_intent");
        String id = intent.path("id").asString(null);
        String code = error.path("decline_code").asString(error.path("code").asString(""));
        if ("authentication_required".equals(code) && intent.isObject()) {
            // Un cobro sin el cliente delante que el emisor quiere autenticar: no es un fallo, es una
            // acción del cliente pendiente (docs/dsl/payments.md § Dos puertas).
            return GatewayOutcome.actionRequired(reference, id, customerAction(intent));
        }
        return GatewayOutcome.failed(reference, id, reasonOf(error));
    }

    private String customerAction(JsonNode intent) {
        ObjectNode action = mapper.createObjectNode();
        action.put("clientSecret", intent.path("client_secret").asString(null));
        action.set("nextAction", intent.path("next_action"));
        return action.toString();
    }

    private FailureReason reasonOf(JsonNode error) {
        String code = error.path("decline_code").asString(error.path("code").asString(""));
        return switch (code) {
            case "insufficient_funds" -> FailureReason.INSUFFICIENT_FUNDS;
            case "expired_card" -> FailureReason.EXPIRED_CARD;
            case "fraudulent" -> FailureReason.FRAUD_SUSPECTED;
            case "stolen_card" -> FailureReason.FRAUD_SUSPECTED;
            case "lost_card" -> FailureReason.FRAUD_SUSPECTED;
            case "pickup_card" -> FailureReason.FRAUD_SUSPECTED;
            case "merchant_blacklist" -> FailureReason.FRAUD_SUSPECTED;
            case "authentication_required" -> FailureReason.AUTHENTICATION_FAILED;
            case "incorrect_cvc" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "incorrect_number" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "invalid_cvc" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "invalid_expiry_month" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "invalid_expiry_year" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "invalid_number" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "card_not_supported" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "resource_missing" -> FailureReason.INVALID_PAYMENT_METHOD;
            case "processing_error" -> FailureReason.PROCESSING_ERROR;
            case "try_again_later" -> FailureReason.PROCESSING_ERROR;
            case "issuer_not_available" -> FailureReason.PROCESSING_ERROR;
            default -> FailureReason.DECLINED;
        };
    }

    private JsonNode json(String body) {
        try {
            return mapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (JacksonException unreadable) {
            throw new IllegalStateException("Stripe devolvió un cuerpo que no es JSON", unreadable);
        }
    }

    private PaymentGatewayUnavailableException unavailable(String action, Exception cause) {
        return new PaymentGatewayUnavailableException("Stripe no contestó a " + action + ": la acción queda en duda", cause);
    }
}
