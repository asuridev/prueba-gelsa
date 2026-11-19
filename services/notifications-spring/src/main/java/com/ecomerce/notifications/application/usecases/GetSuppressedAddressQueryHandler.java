package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.SuppressedAddressNotFoundError;
import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.GetSuppressedAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.SuppressedAddressApplicationMapper;
import com.ecomerce.notifications.application.queries.GetSuppressedAddressQuery;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;

/**
 * Devuelve el registro de supresión de una dirección, esté active o released.
 */
@ApplicationComponent
public class GetSuppressedAddressQueryHandler implements QueryHandler<GetSuppressedAddressQuery, GetSuppressedAddressResponseDto> {

    private final SuppressedAddressRepository suppressedAddressRepository;
    private final SuppressedAddressApplicationMapper suppressedAddressApplicationMapper;

    public GetSuppressedAddressQueryHandler(SuppressedAddressRepository suppressedAddressRepository, SuppressedAddressApplicationMapper suppressedAddressApplicationMapper) {
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.suppressedAddressApplicationMapper = suppressedAddressApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetSuppressedAddressResponseDto handle(GetSuppressedAddressQuery query) {
        SuppressedAddress suppression = suppressedAddressRepository.findByAddress(query.address())
                .orElseThrow(() -> new SuppressedAddressNotFoundError("No existe ningún registro de supresión para esa dirección"));
        return suppressedAddressApplicationMapper.toGetSuppressedAddressResponseDto(suppression);
    }
}
