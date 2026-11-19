package com.ecomerce.notifications.application.usecases;

import java.time.Instant;
import com.ecomerce.notifications.domain.errors.SuppressedAddressNotFoundError;
import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.ReleaseAddressCommand;
import com.ecomerce.notifications.application.dtos.ReleaseAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.SuppressedAddressApplicationMapper;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;

/**
 * Libera una dirección suprimida; el registro se conserva. Liberar una dirección ya released no cambia nada.
 */
@ApplicationComponent
public class ReleaseAddressCommandHandler implements ReturningCommandHandler<ReleaseAddressCommand, ReleaseAddressResponseDto> {

    private final SuppressedAddressRepository suppressedAddressRepository;
    private final SuppressedAddressApplicationMapper suppressedAddressApplicationMapper;

    public ReleaseAddressCommandHandler(SuppressedAddressRepository suppressedAddressRepository, SuppressedAddressApplicationMapper suppressedAddressApplicationMapper) {
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.suppressedAddressApplicationMapper = suppressedAddressApplicationMapper;
    }

    @Override
    @LogExceptions
    public ReleaseAddressResponseDto handle(ReleaseAddressCommand command) {
        SuppressedAddress suppression = suppressedAddressRepository.findByAddress(command.address())
                .orElseThrow(() -> new SuppressedAddressNotFoundError("No existe ningún registro de supresión para esa dirección"));
        if (suppression.isActive()) {
            suppression.release(Instant.now());
            suppression = suppressedAddressRepository.save(suppression);
        }
        // Ya released: se devuelve sin cambios.
        return suppressedAddressApplicationMapper.toReleaseAddressResponseDto(suppression);
    }
}
