package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.PublishTemplateVersionResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Publica contenido nuevo para una plantilla. La versión nueva queda active y la que lo estaba pasa a archived, en la misma transacción.
 */
public record PublishTemplateVersionCommand(
        String templateCode,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 262144) String htmlBody,
        @NotBlank @Size(max = 65536) String textBody,
        @Size(max = 50) @Valid List<TemplateVariable> variables
) implements ReturningCommand<PublishTemplateVersionResponseDto> {

    // Una lista no informada es una lista vacía, nunca null (contrato del cable).
    public PublishTemplateVersionCommand {
        variables = variables == null ? List.of() : variables;
    }
}
