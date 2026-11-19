package com.ecomerce.order.application.queries;

import com.ecomerce.order.application.dtos.ListMyOrdersResponseDto;
import com.ecomerce.order.application.dtos.PagedResponse;
import com.ecomerce.order.application.interfaces.Query;
import com.ecomerce.order.domain.enums.OrderStatus;
import com.ecomerce.order.domain.repository.ReadScope;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.domain.Pageable;

/**
 * Lista los pedidos del comprador que lo pide, del más reciente al más antiguo.
 */
public record ListMyOrdersQuery(
        // La resuelve el servidor desde la credencial (security.authentication.callerIdentity):
        // no llega del cuerpo, la estampa el controller. Por el canal de eventos la estampa el listener.
        @JsonIgnore String customerRef,
        OrderStatus status,
        Pageable pageable,
        // Qué filas ve quien lee (lectura acotada, DSL 2.27): lo estampa el controller generado desde el token. No se
        // acepta del cuerpo ni de la query. Pásalo tal cual al puerto: el adaptador filtra con él.
        @JsonIgnore ReadScope readScope
) implements Query<PagedResponse<ListMyOrdersResponseDto>> {
}
