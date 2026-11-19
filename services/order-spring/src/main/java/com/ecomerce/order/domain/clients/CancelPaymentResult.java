package com.ecomerce.order.domain.clients;

import com.ecomerce.order.domain.enums.PaymentFailureReason;
import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Resultado de payment.cancelPayment en términos del dominio.
 */
public record CancelPaymentResult(UUID id, PaymentStatus status, PaymentFailureReason failureReason) {}
