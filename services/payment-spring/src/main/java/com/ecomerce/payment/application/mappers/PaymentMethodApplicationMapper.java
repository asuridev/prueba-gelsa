package com.ecomerce.payment.application.mappers;

import com.ecomerce.payment.application.annotations.ApplicationComponent;
import com.ecomerce.payment.application.dtos.ListPaymentMethodsResponseDto;
import com.ecomerce.payment.application.dtos.SavePaymentMethodResponseDto;
import com.ecomerce.payment.application.dtos.SetDefaultPaymentMethodResponseDto;
import com.ecomerce.payment.domain.entity.PaymentMethod;
import java.util.UUID;

@ApplicationComponent
public class PaymentMethodApplicationMapper {

    public SavePaymentMethodResponseDto toSavePaymentMethodResponseDto(PaymentMethod entity, UUID walletId) {
        return new SavePaymentMethodResponseDto(
                entity.getId(),
                entity.getCard(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getExpired(),
                walletId);
    }

    public ListPaymentMethodsResponseDto toListPaymentMethodsResponseDto(PaymentMethod entity, UUID walletId) {
        return new ListPaymentMethodsResponseDto(
                entity.getId(),
                entity.getCard(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getExpired(),
                walletId);
    }

    public SetDefaultPaymentMethodResponseDto toSetDefaultPaymentMethodResponseDto(PaymentMethod entity, UUID walletId) {
        return new SetDefaultPaymentMethodResponseDto(
                entity.getId(),
                entity.getCard(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getExpired(),
                walletId);
    }
}
