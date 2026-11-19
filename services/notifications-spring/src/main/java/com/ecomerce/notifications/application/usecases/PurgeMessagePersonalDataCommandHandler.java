package com.ecomerce.notifications.application.usecases;

import com.ecomerce.notifications.application.annotations.ApplicationComponent;
import com.ecomerce.notifications.application.annotations.LogExceptions;
import com.ecomerce.notifications.application.commands.PurgeMessagePersonalDataCommand;
import com.ecomerce.notifications.application.dtos.PurgeMessagePersonalDataResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommandHandler;
import com.ecomerce.notifications.application.support.SweepPass;
import com.ecomerce.notifications.domain.aggregate.EmailMessage;
import com.ecomerce.notifications.domain.parameters.NotificationsParameters;
import com.ecomerce.notifications.domain.repository.EmailMessageRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Vacía los datos personales de los mensajes más antiguos que el plazo de retención (personalDataRetentionMonths) y conserva la fila y su clave para siempre. Diario a las 03:00 UTC y a demanda del administrador.
 */
@ApplicationComponent
public class PurgeMessagePersonalDataCommandHandler implements ReturningCommandHandler<PurgeMessagePersonalDataCommand, PurgeMessagePersonalDataResponseDto> {

    /** «Hasta 5000 mensajes» por ejecución, del diseño. */
    private static final int BATCH = 5000;

    private final EmailMessageRepository emailMessageRepository;
    private final NotificationsParameters parameters;

    public PurgeMessagePersonalDataCommandHandler(EmailMessageRepository emailMessageRepository, NotificationsParameters parameters) {
        this.emailMessageRepository = emailMessageRepository;
        this.parameters = parameters;
    }

    @Override
    @LogExceptions
    public PurgeMessagePersonalDataResponseDto handle(PurgeMessagePersonalDataCommand command) {
        // Meses de calendario, en UTC, restados a ahora.
        Instant cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(parameters.personalDataRetentionMonths()).toInstant();
        // El adaptador toma sus candidatos saltando los que otra ejecución (la programada o la manual) tiene en
        // curso: ninguna falla por solaparse y cada una cuenta solo los suyos.
        List<EmailMessage> candidates = emailMessageRepository.lockPurgeCandidates(cutoff, BATCH);
        SweepPass.claimed(candidates.size(), BATCH);
        Instant now = Instant.now();
        int purged = 0;
        for (EmailMessage message : candidates) {
            if (message.purgePersonalData(now)) {
                emailMessageRepository.save(message);
                purged++;
            }
        }
        return new PurgeMessagePersonalDataResponseDto(purged);
    }
}
