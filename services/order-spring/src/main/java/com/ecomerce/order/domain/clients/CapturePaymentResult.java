package com.ecomerce.order.domain.clients;

import com.ecomerce.order.domain.enums.PaymentStatus;
import java.util.UUID;

/**
 * Resultado de payment.capturePayment en términos del dominio.
 */
public record CapturePaymentResult(UUID id, PaymentStatus status) {}
