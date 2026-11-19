package com.ecomerce.notifications.application.queries;

import com.ecomerce.notifications.application.dtos.ListSuppressedAddressesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.Query;
import org.springframework.data.domain.Pageable;

/**
 * Lista las direcciones con supresión active, de la más reciente a la más antigua.
 */
public record ListSuppressedAddressesQuery(
        Pageable pageable
) implements Query<PagedResponse<ListSuppressedAddressesResponseDto>> {
}
