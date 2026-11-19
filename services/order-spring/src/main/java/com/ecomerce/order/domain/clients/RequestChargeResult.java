package com.ecomerce.order.domain.clients;

import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Resultado de payment.requestCharge en términos del dominio.
 */
public record RequestChargeResult(UUID id, String chargeRequestId, PaymentStatus status, PaymentFailureReason failureReason, String customerAction) {}
