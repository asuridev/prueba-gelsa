package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.dtos.GetOrderResponseDto;
import com.ecomerce.order.application.interfaces.QueryHandler;
import com.ecomerce.order.application.mappers.OrderApplicationMapper;
import com.ecomerce.order.application.queries.GetOrderQuery;
import com.ecomerce.order.domain.errors.OrderNotFoundError;
import com.ecomerce.order.domain.repository.OrderRepository;

/**
 * Devuelve un pedido del comprador que lo pide, con sus líneas y el estado de su cobro.
 */
@ApplicationComponent
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, GetOrderResponseDto> {

    private final OrderRepository orderRepository;
    private final OrderApplicationMapper orderApplicationMapper;

    public GetOrderQueryHandler(OrderRepository orderRepository, OrderApplicationMapper orderApplicationMapper) {
        this.orderRepository = orderRepository;
        this.orderApplicationMapper = orderApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetOrderResponseDto handle(GetOrderQuery query) {
        // Carga acotada: una fila ajena da el mismo 404 que una que no existe.
        return orderRepository.loadScoped(query.id(), query.readScope())
                .map(orderApplicationMapper::toGetOrderResponseDto)
                .orElseThrow(() -> new OrderNotFoundError("No existe el pedido " + query.id()));
    }
}
