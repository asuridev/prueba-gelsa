package com.ecomerce.payment.infrastructure.payment.stripe;

import com.ecomerce.payment.infrastructure.payment.InvalidPaymentNoticeException;
import com.ecomerce.payment.infrastructure.payment.PaymentGatewayProperties;
import com.ecomerce.payment.infrastructure.payment.PaymentNoticeVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifica la cabecera Stripe-Signature: {@code t=<timestamp>,v1=<firma>[,v1=<firma>]}.
 *
 * <p>La firma es HMAC-SHA256 de {@code <t>.<cuerpo crudo>} con el secreto del endpoint. Solo cuenta
 * {@code v1} (el resto se ignora, contra el downgrade); puede haber varias durante la rotación del
 * secreto y basta con que verifique una; se compara en tiempo constante; y un {@code t} más antiguo
 * que la tolerancia se rechaza aunque la firma sea buena, porque es un aviso repetido.
 */
@Component
public class StripeNoticeVerifier implements PaymentNoticeVerifier {

    private final PaymentGatewayProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public StripeNoticeVerifier(PaymentGatewayProperties properties, ObjectMapper mapper) {
        this(properties, mapper, Clock.systemUTC());
    }

    StripeNoticeVerifier(PaymentGatewayProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<String> verify(byte[] body, HttpHeaders headers, Map<String, String> query) {
        String header = headers.getFirst("Stripe-Signature");
        if (header == null || header.isBlank()) {
            throw new InvalidPaymentNoticeException("sin cabecera de firma");
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException malformed) {
                    throw new InvalidPaymentNoticeException("timestamp ilegible");
                }
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidPaymentNoticeException("cabecera de firma incompleta");
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > properties.noticeToleranceSeconds()) {
            throw new InvalidPaymentNoticeException("aviso fuera de la ventana de tolerancia");
        }
        byte[] expected = hmac(timestamp + "." + new String(body, StandardCharsets.UTF_8));
        boolean valid = signatures.stream()
                .anyMatch(signature -> MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8)));
        if (!valid) {
            throw new InvalidPaymentNoticeException("la firma no verifica");
        }
        return paymentIntentOf(body);
    }

    /** El PaymentIntent del que habla el evento: el propio objeto, o el payment_intent de un cargo o una devolución. */
    private Optional<String> paymentIntentOf(byte[] body) {
        try {
            JsonNode object = mapper.readTree(body).path("data").path("object");
            if ("payment_intent".equals(object.path("object").asString())) {
                return Optional.ofNullable(object.path("id").asString(null));
            }
            return Optional.ofNullable(object.path("payment_intent").asString(null));
        } catch (tools.jackson.core.JacksonException unreadable) {
            throw new InvalidPaymentNoticeException("el cuerpo no es JSON");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 no disponible", impossible);
        }
    }
}
