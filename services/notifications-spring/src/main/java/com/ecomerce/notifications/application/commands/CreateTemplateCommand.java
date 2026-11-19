package com.ecomerce.notifications.application.commands;

import com.ecomerce.notifications.application.dtos.CreateTemplateResponseDto;
import com.ecomerce.notifications.application.interfaces.ReturningCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Da de alta una plantilla vacía, sin versiones; no se puede usar hasta publicar la primera.
 */
public record CreateTemplateCommand(
        // El @Pattern del value type TemplateCode (^[a-z][a-z0-9-]{2,47}$) se valida YA aquí: mal formado es
        // un 400 antes que cualquier precondición. No lo valides otra vez al principio del handler.
        // El dominio lo repite con TemplateCodeFormat.validate(...) en el factory o el método de
        // negocio de la entidad que recibe el valor: cubre lo que no entra por la API (un evento, una
        // operación interna), y lo exige el gate check-domain-guards.sh.
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,47}$") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description
) implements ReturningCommand<CreateTemplateResponseDto> {
}
