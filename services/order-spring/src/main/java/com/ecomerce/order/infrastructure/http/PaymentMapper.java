package com.ecomerce.order.infrastructure.http;

import com.ecomerce.order.domain.clients.CancelPaymentResult;
import com.ecomerce.order.domain.clients.CapturePaymentResult;
import com.ecomerce.order.domain.clients.GetPaymentByChargeRequestResult;
import com.ecomerce.order.domain.clients.RequestChargeResult;
import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Capa de anticorrupción de payment: traduce entre el contrato wire del
 * sistema externo y los tipos del dominio. Si el tercero cambia su contrato,
 * el cambio se absorbe aquí (y en los DTOs wire), nunca en el dominio.
 */
@Component
public class PaymentMapper {

    /** Traduce la respuesta wire de requestCharge al resultado del dominio. */
    public RequestChargeResult toRequestChargeResult(RequestChargeResponse response) {
        return new RequestChargeResult(response.id(), response.chargeRequestId(), response.status(), response.failureReason(), response.customerAction());
    }

    /** Arma el body wire de requestCharge desde los valores del dominio. */
    public RequestChargeRequest toRequestChargeRequest(String chargeRequestId, String customerRef, BigDecimal amount, String currency, String paymentToken, UUID paymentMethodId) {
        return new RequestChargeRequest(chargeRequestId, customerRef, amount, currency, paymentToken, paymentMethodId);
    }

    /** Traduce la respuesta wire de capturePayment al resultado del dominio. */
    public CapturePaymentResult toCapturePaymentResult(CapturePaymentResponse response) {
        return new CapturePaymentResult(response.id(), response.status());
    }

    /** Traduce la respuesta wire de cancelPayment al resultado del dominio. */
    public CancelPaymentResult toCancelPaymentResult(CancelPaymentResponse response) {
        return new CancelPaymentResult(response.id(), response.status(), response.failureReason());
    }

    /** Traduce la respuesta wire de getPaymentByChargeRequest al resultado del dominio. */
    public GetPaymentByChargeRequestResult toGetPaymentByChargeRequestResult(GetPaymentByChargeRequestResponse response) {
        return new GetPaymentByChargeRequestResult(response.id(), response.status(), response.failureReason(), response.customerAction());
    }
}
