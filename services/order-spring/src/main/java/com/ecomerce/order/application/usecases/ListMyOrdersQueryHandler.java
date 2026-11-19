package com.ecomerce.order.application.usecases;

import com.ecomerce.order.application.annotations.ApplicationComponent;
import com.ecomerce.order.application.annotations.LogExceptions;
import com.ecomerce.order.application.dtos.ListMyOrdersResponseDto;
import com.ecomerce.order.application.dtos.PagedResponse;
import com.ecomerce.order.application.interfaces.QueryHandler;
import com.ecomerce.order.application.mappers.OrderApplicationMapper;
import com.ecomerce.order.application.queries.ListMyOrdersQuery;
import com.ecomerce.order.domain.repository.OrderRepository;

/**
 * Lista los pedidos del comprador que lo pide, del más reciente al más antiguo.
 */
@ApplicationComponent
public class ListMyOrdersQueryHandler implements QueryHandler<ListMyOrdersQuery, PagedResponse<ListMyOrdersResponseDto>> {

    private final OrderRepository orderRepository;
    private final OrderApplicationMapper orderApplicationMapper;

    public ListMyOrdersQueryHandler(OrderRepository orderRepository, OrderApplicationMapper orderApplicationMapper) {
        this.orderRepository = orderRepository;
        this.orderApplicationMapper = orderApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListMyOrdersResponseDto> handle(ListMyOrdersQuery query) {
        return PagedResponse.from(orderRepository
                .listMyOrders(new OrderRepository.ListMyOrdersFilter(query.customerRef(), query.status()), query.pageable(), query.readScope())
                .map(orderApplicationMapper::toListMyOrdersResponseDto));
    }
}
