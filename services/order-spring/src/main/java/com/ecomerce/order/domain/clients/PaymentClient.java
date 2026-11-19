package com.ecomerce.order.domain.clients;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cobrar, capturar, anular y consultar los cobros de los pedidos en payment (contrato 0.1.4).
 * Puerto de salida del dominio; el adaptador HTTP (PaymentHttpAdapter)
 * vive en infrastructure/http.
 */
public interface PaymentClient {

    /** Pide el cobro de un pedido. Idempotente por chargeRequestId (clave de negocio en el cuerpo, permanente): repetir la misma petición devuelve el mismo cobro sin volver a llamar a la pasarela, así que reintentar con el mismo cuerpo es seguro. No usa la cabecera Idempotency-Key. Un rechazo de la pasarela no es un error HTTP: llega como status failed. */
    RequestChargeResult requestCharge(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId);

    /** Captura un cobro autorizado. Idempotente por estado en payment: en capturing, captured, refunding o refunded devuelve el cobro tal cual sin volver a llamar a la pasarela. */
    CapturePaymentResult capturePayment(UUID id);

    /** Anula un cobro en authorized o actionRequired. Idempotente por estado en payment: en canceling o canceled devuelve el cobro tal cual. */
    CancelPaymentResult cancelPayment(UUID id);

    /** Devuelve el cobro registrado con un chargeRequestId; 404 PAYMENT_NOT_FOUND si payment nunca lo registró. */
    GetPaymentByChargeRequestResult getPaymentByChargeRequest(String chargeRequestId);
}
