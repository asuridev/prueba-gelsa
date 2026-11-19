package com.ecomerce.order.domain.clients;

import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Resultado de payment.getPaymentByChargeRequest en términos del dominio.
 */
public record GetPaymentByChargeRequestResult(UUID id, PaymentStatus status, PaymentFailureReason failureReason, String customerAction) {}
