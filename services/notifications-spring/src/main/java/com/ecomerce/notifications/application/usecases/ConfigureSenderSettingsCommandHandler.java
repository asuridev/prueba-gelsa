package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.aggregate.SenderSettings;
import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.ConfigureSenderSettingsCommand;
import com.ecomerce.notifications.application.dtos.ConfigureSenderSettingsResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.mappers.SenderSettingsApplicationMapper;
import com.ecomerce.notifications.domain.repository.SenderSettingsRepository;

/**
 * Crea o reemplaza el remitente del servicio. Los mensajes ya aceptados conservan el remitente que congelaron.
 */
@ApplicationComponent
public class ConfigureSenderSettingsCommandHandler implements ReturningCommandHandler<ConfigureSenderSettingsCommand, ConfigureSenderSettingsResponseDto> {

    private final SenderSettingsRepository senderSettingsRepository;
    private final SenderSettingsApplicationMapper senderSettingsApplicationMapper;

    public ConfigureSenderSettingsCommandHandler(SenderSettingsRepository senderSettingsRepository, SenderSettingsApplicationMapper senderSettingsApplicationMapper) {
        this.senderSettingsRepository = senderSettingsRepository;
        this.senderSettingsApplicationMapper = senderSettingsApplicationMapper;
    }

    @Override
    @LogExceptions
    public ConfigureSenderSettingsResponseDto handle(ConfigureSenderSettingsCommand command) {
        // Las direcciones se normalizan a minúsculas y se validan en el dominio; un campo omitido queda vacío.
        SenderSettings settings = senderSettingsRepository.findByKey(SenderSettingsKey.DEFAULT).orElse(null);
        if (settings == null) {
            settings = SenderSettings.create(command.senderAddress(), command.senderName(), command.replyToAddress());
        } else {
            settings.replace(command.senderAddress(), command.senderName(), command.replyToAddress());
        }
        return senderSettingsApplicationMapper.toConfigureSenderSettingsResponseDto(senderSettingsRepository.save(settings));
    }
}
