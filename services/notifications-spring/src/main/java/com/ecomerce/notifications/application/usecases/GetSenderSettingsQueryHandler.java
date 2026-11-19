package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.errors.SenderSettingsNotConfiguredError;
import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.dtos.GetSenderSettingsResponseDto;
import com.ecomerce.notifications.application.interfaces.QueryHandler;
import com.ecomerce.notifications.application.mappers.SenderSettingsApplicationMapper;
import com.ecomerce.notifications.application.queries.GetSenderSettingsQuery;
import com.ecomerce.notifications.domain.repository.SenderSettingsRepository;

/**
 * Devuelve el remitente configurado.
 */
@ApplicationComponent
public class GetSenderSettingsQueryHandler implements QueryHandler<GetSenderSettingsQuery, GetSenderSettingsResponseDto> {

    private final SenderSettingsRepository senderSettingsRepository;
    private final SenderSettingsApplicationMapper senderSettingsApplicationMapper;

    public GetSenderSettingsQueryHandler(SenderSettingsRepository senderSettingsRepository, SenderSettingsApplicationMapper senderSettingsApplicationMapper) {
        this.senderSettingsRepository = senderSettingsRepository;
        this.senderSettingsApplicationMapper = senderSettingsApplicationMapper;
    }

    @Override
    @LogExceptions
    public GetSenderSettingsResponseDto handle(GetSenderSettingsQuery query) {
        SenderSettings settings = senderSettingsRepository.findByKey(SenderSettingsKey.DEFAULT)
                .orElseThrow(() -> new SenderSettingsNotConfiguredError("Todavía no se ha configurado el remitente"));
        return senderSettingsApplicationMapper.toGetSenderSettingsResponseDto(settings);
    }
}
