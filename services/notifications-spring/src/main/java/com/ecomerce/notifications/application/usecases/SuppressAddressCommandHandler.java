package com.ecomerce.notifications.application.usecases;

import java.util.Optional;
import java.time.Instant;
import com.ecomerce.notifications.domain.aggregate.SuppressedAddress;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.SuppressAddressCommand;
import com.ecomerce.notifications.application.dtos.SuppressAddressResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.SuppressedAddressApplicationMapper;
import com.ecomerce.notifications.domain.repository.SuppressedAddressRepository;

/**
 * Suprime una dirección a mano. Reactiva el registro si estaba released; si ya está active, no cambia nada.
 */
@ApplicationComponent
public class SuppressAddressCommandHandler implements ReturningCommandHandler<SuppressAddressCommand, SuppressAddressResponseDto> {

    private final SuppressedAddressRepository suppressedAddressRepository;
    private final SuppressedAddressApplicationMapper suppressedAddressApplicationMapper;

    public SuppressAddressCommandHandler(SuppressedAddressRepository suppressedAddressRepository, SuppressedAddressApplicationMapper suppressedAddressApplicationMapper) {
        this.suppressedAddressRepository = suppressedAddressRepository;
        this.suppressedAddressApplicationMapper = suppressedAddressApplicationMapper;
    }

    @Override
    @LogExceptions
    public SuppressAddressResponseDto handle(SuppressAddressCommand command) {
        Instant now = Instant.now();
        Optional<SuppressedAddress> existing = suppressedAddressRepository.findByAddress(command.address());
        SuppressedAddress result;
        if (existing.isEmpty()) {
            // La dirección se normaliza a minúsculas en el factory; la carrera con otra alta la arbitra la clave natural.
            result = suppressedAddressRepository.save(
                    SuppressedAddress.create(command.address(), command.reason(), command.notes(), now));
        } else if (existing.get().isActive()) {
            // Ya active: se devuelve sin cambios.
            result = existing.get();
        } else {
            SuppressedAddress suppression = existing.get();
            suppression.reactivate(command.reason(), command.notes(), now);
            result = suppressedAddressRepository.save(suppression);
        }
        return suppressedAddressApplicationMapper.toSuppressAddressResponseDto(result);
    }
}
