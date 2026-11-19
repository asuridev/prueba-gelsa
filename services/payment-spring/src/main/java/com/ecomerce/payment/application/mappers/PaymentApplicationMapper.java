package com.ecomerce.payment.application.mappers;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.dtos.CancelPaymentResponseDto;
import com.ecomerce.payment.application.dtos.CapturePaymentResponseDto;
import com.ecomerce.payment.application.dtos.GetPaymentByChargeRequestResponseDto;
import com.ecomerce.payment.application.dtos.GetPaymentResponseDto;
import com.ecomerce.payment.application.dtos.ListPaymentsByCustomerResponseDto;
import com.ecomerce.payment.application.dtos.RefundDto;
import com.ecomerce.payment.application.dtos.RefundPaymentResponseDto;
import com.ecomerce.payment.application.dtos.RequestChargeResponseDto;
import com.ecomerce.payment.domain.aggregate.Payment;
import com.ecomerce.payment.domain.entity.Refund;
import java.util.UUID;

@ApplicationComponent
public class PaymentApplicationMapper {

    public RequestChargeResponseDto toRequestChargeResponseDto(Payment entity) {
        return new RequestChargeResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public CapturePaymentResponseDto toCapturePaymentResponseDto(Payment entity) {
        return new CapturePaymentResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public CancelPaymentResponseDto toCancelPaymentResponseDto(Payment entity) {
        return new CancelPaymentResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public RefundPaymentResponseDto toRefundPaymentResponseDto(Payment entity) {
        return new RefundPaymentResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public GetPaymentResponseDto toGetPaymentResponseDto(Payment entity) {
        return new GetPaymentResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public GetPaymentByChargeRequestResponseDto toGetPaymentByChargeRequestResponseDto(Payment entity) {
        return new GetPaymentByChargeRequestResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public ListPaymentsByCustomerResponseDto toListPaymentsByCustomerResponseDto(Payment entity) {
        return new ListPaymentsByCustomerResponseDto(
                entity.getId(),
                entity.getChargeRequestId(),
                entity.getCustomerRef(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getGatewayPaymentId(),
                entity.getAwaitingSince(),
                entity.getFailureReason(),
                entity.getCustomerAction(),
                entity.getActionRequiredSince(),
                entity.getCancelOrigin(),
                entity.getPaymentMethodId(),
                entity.getRefundedAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRefunds().stream().map(child -> toRefundDto(child, entity.getId())).toList());
    }

    public RefundDto toRefundDto(Refund entity, UUID paymentId) {
        return new RefundDto(
                entity.getId(),
                entity.getRefundRequestId(),
                entity.getAmount(),
                entity.getReason(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                paymentId);
    }
}
