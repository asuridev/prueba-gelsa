package com.ecomerce.order.application.mappers;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.dtos.CancelOrderResponseDto;
import com.ecomerce.order.application.dtos.CreateOrderResponseDto;
import com.ecomerce.order.application.dtos.GetOrderResponseDto;
import com.ecomerce.order.application.dtos.ListMyOrdersResponseDto;
import com.ecomerce.order.application.dtos.OrderLineDto;
import com.ecomerce.order.domain.aggregate.Order;
import com.ecomerce.order.domain.entity.OrderLine;

@ApplicationComponent
public class OrderApplicationMapper {

    public CreateOrderResponseDto toCreateOrderResponseDto(Order entity) {
        return new CreateOrderResponseDto(
                entity.getId(),
                entity.getCustomerRef(),
                entity.getCustomerEmail(),
                entity.getStatus(),
                entity.getCurrency(),
                entity.getTotalAmount(),
                entity.getLineCount(),
                entity.getPaymentId(),
                entity.getCustomerAction(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLines().stream().map(l -> toOrderLineDto(l, entity.getId())).toList());
    }

    public GetOrderResponseDto toGetOrderResponseDto(Order entity) {
        return new GetOrderResponseDto(
                entity.getId(),
                entity.getCustomerRef(),
                entity.getCustomerEmail(),
                entity.getStatus(),
                entity.getCurrency(),
                entity.getTotalAmount(),
                entity.getLineCount(),
                entity.getPaymentId(),
                entity.getCustomerAction(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLines().stream().map(l -> toOrderLineDto(l, entity.getId())).toList());
    }

    public ListMyOrdersResponseDto toListMyOrdersResponseDto(Order entity) {
        return new ListMyOrdersResponseDto(
                entity.getId(),
                entity.getCustomerRef(),
                entity.getCustomerEmail(),
                entity.getStatus(),
                entity.getCurrency(),
                entity.getTotalAmount(),
                entity.getLineCount(),
                entity.getPaymentId(),
                entity.getCustomerAction(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLines().stream().map(l -> toOrderLineDto(l, entity.getId())).toList());
    }

    public CancelOrderResponseDto toCancelOrderResponseDto(Order entity) {
        return new CancelOrderResponseDto(
                entity.getId(),
                entity.getCustomerRef(),
                entity.getCustomerEmail(),
                entity.getStatus(),
                entity.getCurrency(),
                entity.getTotalAmount(),
                entity.getLineCount(),
                entity.getPaymentId(),
                entity.getCustomerAction(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLines().stream().map(l -> toOrderLineDto(l, entity.getId())).toList());
    }

    public OrderLineDto toOrderLineDto(OrderLine entity, java.util.UUID orderId) {
        return new OrderLineDto(
                entity.getId(),
                orderId,
                
                entity.getProductId(),
                entity.getSku(),
                entity.getProductName(),
                entity.getUnitPrice(),
                entity.getQuantity());
    }
}
