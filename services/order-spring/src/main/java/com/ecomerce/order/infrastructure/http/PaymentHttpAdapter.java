package com.ecomerce.order.infrastructure.http;

import com.ecomerce.order.domain.clients.CancelPaymentResult;
import com.ecomerce.order.domain.clients.CapturePaymentResult;
import com.ecomerce.order.domain.clients.GetPaymentByChargeRequestResult;
import com.ecomerce.order.domain.clients.PaymentClient;
import com.ecomerce.order.domain.clients.PaymentRejectedException;
import com.ecomerce.order.domain.clients.PaymentUnavailableException;
import com.ecomerce.order.domain.clients.RequestChargeResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.UnknownHttpStatusCodeException;

@Component
public class PaymentHttpAdapter implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentHttpAdapter.class);

    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\s*:\s*\"([A-Za-z0-9_.-]+)\"");

    /**
     * El 4xx de payment, traducido: un 401 o 403 es un fallo de credenciales M2M y degrada como si no
     * contestara; cualquier otro es un rechazo de negocio o de validación, con el code de su cuerpo.
     */
    private static RuntimeException rejection(HttpClientErrorException error) {
        int status = error.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new PaymentUnavailableException("payment rechazó las credenciales (" + status + ")", error);
        }
        Matcher matcher = ERROR_CODE.matcher(error.getResponseBodyAsString());
        return new PaymentRejectedException(status, matcher.find() ? matcher.group(1) : null, error);
    }

    private final RestClient restClient;
    private final PaymentMapper mapper;

    public PaymentHttpAdapter(@Qualifier("paymentRestClient") RestClient restClient, PaymentMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    @Retry(name = "payment-request-charge", fallbackMethod = "requestChargeFallback")
    @CircuitBreaker(name = "payment-request-charge")
    public RequestChargeResult requestCharge(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId) {
        RequestChargeResponse response = restClient.post()
                .uri("/payments")
                .body(mapper.toRequestChargeRequest(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId))
                .retrieve()
                .body(RequestChargeResponse.class);
        if (response == null) {
            log.warn("payment.requestCharge respondió sin cuerpo; el contrato declara 5 campo(s)");
            return new RequestChargeResult(null, null, null, null, null);
        }
        return mapper.toRequestChargeResult(response);
    }

    /** El circuito está abierto: la llamada ni se intentó. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, CallNotPermittedException throwable) {
        return requestChargeUnavailable(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId, throwable);
    }

    /** Fallo de transporte: conexión rechazada, timeout de lectura, DNS. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, ResourceAccessException throwable) {
        return requestChargeUnavailable(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId, throwable);
    }

    /** El proveedor contestó 5xx. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, HttpServerErrorException throwable) {
        return requestChargeUnavailable(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId, throwable);
    }

    /** El proveedor contestó un status que no existe en el estándar. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, UnknownHttpStatusCodeException throwable) {
        return requestChargeUnavailable(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId, throwable);
    }

    /** El proveedor RECHAZA la petición (4xx): contestó, no está caído. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, HttpClientErrorException throwable) {
        // Si el rechazo tiene significado de negocio —un 404 es "no existe", un 409 un
        // conflicto suyo—, la traducción va con .onStatus(...) en la llamada, no aquí.
        log.warn("payment.requestCharge rechazada por el proveedor ({})", throwable.getStatusCode());
        throw rejection(throwable);
    }

    /** No se pudo obtener el token: el proveedor de identidad no responde o nos rechaza. */
    private RequestChargeResult requestChargeFallback(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, OAuth2AuthorizationException throwable) {
        return requestChargeUnavailable(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId, throwable);
    }

    /**
     * Política del diseño para cuando payment.requestCharge no sale.
     *
     * Solo llega aquí lo que ha hecho el PROVEEDOR: no responder, responder mal o
     * rechazarnos. Un error de programación de este adaptador —un NPE, un cast, un
     * cuerpo que no deserializa— no tiene sobrecarga que lo enrute y se propaga con
     * su traza. No lo ensanches a `Exception` ni a `Throwable` para "que no se
     * escape nada": lo que se escapa es el bug, y taparlo aquí es lo que mantuvo uno
     * meses disfrazado de caída ajena.
     */
    private RequestChargeResult requestChargeUnavailable(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId, Throwable throwable) {
        log.warn("Fallback de payment.requestCharge", throwable);
        // Fallback declarado en el diseño: El pedido queda en pendingPayment y la operación responde con él (onFailure degrade de la activación chargeOrder).
        // Política declarada por la activación payment.chargeOrder (onFailure: degrade).
        throw new PaymentUnavailableException("payment.requestCharge no disponible: degrada el caso de uso", throwable);
    }

    @Override
    @Retry(name = "payment-capture-payment", fallbackMethod = "capturePaymentFallback")
    @CircuitBreaker(name = "payment-capture-payment")
    public CapturePaymentResult capturePayment(UUID id) {
        CapturePaymentResponse response = restClient.post()
                .uri("/payments/{id}/capture", id)
                .retrieve()
                .body(CapturePaymentResponse.class);
        if (response == null) {
            log.warn("payment.capturePayment respondió sin cuerpo; el contrato declara 2 campo(s)");
            return new CapturePaymentResult(null, null);
        }
        return mapper.toCapturePaymentResult(response);
    }

    /** El circuito está abierto: la llamada ni se intentó. */
    private CapturePaymentResult capturePaymentFallback(UUID id, CallNotPermittedException throwable) {
        return capturePaymentUnavailable(id, throwable);
    }

    /** Fallo de transporte: conexión rechazada, timeout de lectura, DNS. */
    private CapturePaymentResult capturePaymentFallback(UUID id, ResourceAccessException throwable) {
        return capturePaymentUnavailable(id, throwable);
    }

    /** El proveedor contestó 5xx. */
    private CapturePaymentResult capturePaymentFallback(UUID id, HttpServerErrorException throwable) {
        return capturePaymentUnavailable(id, throwable);
    }

    /** El proveedor contestó un status que no existe en el estándar. */
    private CapturePaymentResult capturePaymentFallback(UUID id, UnknownHttpStatusCodeException throwable) {
        return capturePaymentUnavailable(id, throwable);
    }

    /** El proveedor RECHAZA la petición (4xx): contestó, no está caído. */
    private CapturePaymentResult capturePaymentFallback(UUID id, HttpClientErrorException throwable) {
        // Si el rechazo tiene significado de negocio —un 404 es "no existe", un 409 un
        // conflicto suyo—, la traducción va con .onStatus(...) en la llamada, no aquí.
        log.warn("payment.capturePayment rechazada por el proveedor ({})", throwable.getStatusCode());
        throw rejection(throwable);
    }

    /** No se pudo obtener el token: el proveedor de identidad no responde o nos rechaza. */
    private CapturePaymentResult capturePaymentFallback(UUID id, OAuth2AuthorizationException throwable) {
        return capturePaymentUnavailable(id, throwable);
    }

    /**
     * Política del diseño para cuando payment.capturePayment no sale.
     *
     * Solo llega aquí lo que ha hecho el PROVEEDOR: no responder, responder mal o
     * rechazarnos. Un error de programación de este adaptador —un NPE, un cast, un
     * cuerpo que no deserializa— no tiene sobrecarga que lo enrute y se propaga con
     * su traza. No lo ensanches a `Exception` ni a `Throwable` para "que no se
     * escape nada": lo que se escapa es el bug, y taparlo aquí es lo que mantuvo uno
     * meses disfrazado de caída ajena.
     */
    private CapturePaymentResult capturePaymentUnavailable(UUID id, Throwable throwable) {
        log.warn("Fallback de payment.capturePayment", throwable);
        // Fallback declarado en el diseño: El pedido sigue en authorized y el barrido reintenta la captura (onFailure degrade de captureOrderPayment).
        // Política declarada por la activación payment.captureOrderPayment (onFailure: degrade).
        throw new PaymentUnavailableException("payment.capturePayment no disponible: degrada el caso de uso", throwable);
    }

    @Override
    @Retry(name = "payment-cancel-payment", fallbackMethod = "cancelPaymentFallback")
    @CircuitBreaker(name = "payment-cancel-payment")
    public CancelPaymentResult cancelPayment(UUID id) {
        CancelPaymentResponse response = restClient.post()
                .uri("/payments/{id}/cancel", id)
                .retrieve()
                .body(CancelPaymentResponse.class);
        if (response == null) {
            log.warn("payment.cancelPayment respondió sin cuerpo; el contrato declara 3 campo(s)");
            return new CancelPaymentResult(null, null, null);
        }
        return mapper.toCancelPaymentResult(response);
    }

    /** El circuito está abierto: la llamada ni se intentó. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, CallNotPermittedException throwable) {
        return cancelPaymentUnavailable(id, throwable);
    }

    /** Fallo de transporte: conexión rechazada, timeout de lectura, DNS. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, ResourceAccessException throwable) {
        return cancelPaymentUnavailable(id, throwable);
    }

    /** El proveedor contestó 5xx. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, HttpServerErrorException throwable) {
        return cancelPaymentUnavailable(id, throwable);
    }

    /** El proveedor contestó un status que no existe en el estándar. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, UnknownHttpStatusCodeException throwable) {
        return cancelPaymentUnavailable(id, throwable);
    }

    /** El proveedor RECHAZA la petición (4xx): contestó, no está caído. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, HttpClientErrorException throwable) {
        // Si el rechazo tiene significado de negocio —un 404 es "no existe", un 409 un
        // conflicto suyo—, la traducción va con .onStatus(...) en la llamada, no aquí.
        log.warn("payment.cancelPayment rechazada por el proveedor ({})", throwable.getStatusCode());
        throw rejection(throwable);
    }

    /** No se pudo obtener el token: el proveedor de identidad no responde o nos rechaza. */
    private CancelPaymentResult cancelPaymentFallback(UUID id, OAuth2AuthorizationException throwable) {
        return cancelPaymentUnavailable(id, throwable);
    }

    /**
     * Política del diseño para cuando payment.cancelPayment no sale.
     *
     * Solo llega aquí lo que ha hecho el PROVEEDOR: no responder, responder mal o
     * rechazarnos. Un error de programación de este adaptador —un NPE, un cast, un
     * cuerpo que no deserializa— no tiene sobrecarga que lo enrute y se propaga con
     * su traza. No lo ensanches a `Exception` ni a `Throwable` para "que no se
     * escape nada": lo que se escapa es el bug, y taparlo aquí es lo que mantuvo uno
     * meses disfrazado de caída ajena.
     */
    private CancelPaymentResult cancelPaymentUnavailable(UUID id, Throwable throwable) {
        log.warn("Fallback de payment.cancelPayment", throwable);
        // Fallback declarado en el diseño: El pedido queda en canceling y el barrido reintenta la anulación (onFailure degrade de cancelOrderPayment).
        // Política declarada por la activación payment.cancelOrderPayment (onFailure: degrade).
        throw new PaymentUnavailableException("payment.cancelPayment no disponible: degrada el caso de uso", throwable);
    }

    @Override
    @Retry(name = "payment-get-payment-by-charge-request", fallbackMethod = "getPaymentByChargeRequestFallback")
    @CircuitBreaker(name = "payment-get-payment-by-charge-request")
    public GetPaymentByChargeRequestResult getPaymentByChargeRequest(String chargeRequestId) {
        GetPaymentByChargeRequestResponse response = restClient.get()
                .uri("/payments/by-charge-request/{chargeRequestId}", chargeRequestId)
                .retrieve()
                .body(GetPaymentByChargeRequestResponse.class);
        if (response == null) {
            log.warn("payment.getPaymentByChargeRequest respondió sin cuerpo; el contrato declara 4 campo(s)");
            return new GetPaymentByChargeRequestResult(null, null, null, null);
        }
        return mapper.toGetPaymentByChargeRequestResult(response);
    }

    /** El circuito está abierto: la llamada ni se intentó. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, CallNotPermittedException throwable) {
        return getPaymentByChargeRequestUnavailable(chargeRequestId, throwable);
    }

    /** Fallo de transporte: conexión rechazada, timeout de lectura, DNS. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, ResourceAccessException throwable) {
        return getPaymentByChargeRequestUnavailable(chargeRequestId, throwable);
    }

    /** El proveedor contestó 5xx. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, HttpServerErrorException throwable) {
        return getPaymentByChargeRequestUnavailable(chargeRequestId, throwable);
    }

    /** El proveedor contestó un status que no existe en el estándar. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, UnknownHttpStatusCodeException throwable) {
        return getPaymentByChargeRequestUnavailable(chargeRequestId, throwable);
    }

    /** El proveedor RECHAZA la petición (4xx): contestó, no está caído. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, HttpClientErrorException throwable) {
        // Si el rechazo tiene significado de negocio —un 404 es "no existe", un 409 un
        // conflicto suyo—, la traducción va con .onStatus(...) en la llamada, no aquí.
        log.warn("payment.getPaymentByChargeRequest rechazada por el proveedor ({})", throwable.getStatusCode());
        throw rejection(throwable);
    }

    /** No se pudo obtener el token: el proveedor de identidad no responde o nos rechaza. */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestFallback(String chargeRequestId, OAuth2AuthorizationException throwable) {
        return getPaymentByChargeRequestUnavailable(chargeRequestId, throwable);
    }

    /**
     * Política del diseño para cuando payment.getPaymentByChargeRequest no sale.
     *
     * Solo llega aquí lo que ha hecho el PROVEEDOR: no responder, responder mal o
     * rechazarnos. Un error de programación de este adaptador —un NPE, un cast, un
     * cuerpo que no deserializa— no tiene sobrecarga que lo enrute y se propaga con
     * su traza. No lo ensanches a `Exception` ni a `Throwable` para "que no se
     * escape nada": lo que se escapa es el bug, y taparlo aquí es lo que mantuvo uno
     * meses disfrazado de caída ajena.
     */
    private GetPaymentByChargeRequestResult getPaymentByChargeRequestUnavailable(String chargeRequestId, Throwable throwable) {
        log.warn("Fallback de payment.getPaymentByChargeRequest", throwable);
        // Fallback declarado en el diseño: El pedido sigue esperando y se consulta en el siguiente ciclo (onUnavailable degrade de chargeStatus).
        // Política declarada por el need payment.chargeStatus (onUnavailable: degrade).
        throw new PaymentUnavailableException("payment.getPaymentByChargeRequest no disponible: degrada el caso de uso", throwable);
    }
}
