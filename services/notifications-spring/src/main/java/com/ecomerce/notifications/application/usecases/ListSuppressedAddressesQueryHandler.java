package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.ListSuppressedAddressesResponseDto;
import com.ecomerce.notifications.application.dtos.PagedResponse;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.SuppressedAddressApplicationMapper;
import com.ecomerce.notifications.application.queries.ListSuppressedAddressesQuery;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;

/**
 * Lista las direcciones con supresión active, de la más reciente a la más antigua.
 */
@ApplicationComponent
public class ListSuppressedAddressesQueryHandler implements QueryHandler<ListSuppressedAddressesQuery, PagedResponse<ListSuppressedAddressesResponseDto>> {

    private final SuppressedAddressRepository suppressedAddressRepository;
    private final SuppressedAddressApplicationMapper suppressedAddressApplicationMapper;

    public ListSuppressedAddressesQueryHandler(SuppressedAddressRepository suppressedAddressRepository, SuppressedAddressApplicationMapper suppressedAddressApplicationMapper) {
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.suppressedAddressApplicationMapper = suppressedAddressApplicationMapper;
    }

    @Override
    @LogExceptions
    public PagedResponse<ListSuppressedAddressesResponseDto> handle(ListSuppressedAddressesQuery query) {
        return PagedResponse.from(suppressedAddressRepository.listActive(query.pageable())
                .map(suppressedAddressApplicationMapper::toListSuppressedAddressesResponseDto));
    }
}
