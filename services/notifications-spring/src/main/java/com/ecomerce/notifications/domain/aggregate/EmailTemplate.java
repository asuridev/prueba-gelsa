package com.ecomerce.notifications.domain.aggregate;

import com.ecomerce.notifications.domain.entity.EmailTemplateVersion;
import com.ecomerce.notifications.domain.errors.InvariantViolatedException;
import com.ecomerce.notifications.domain.errors.TemplateNotPublishedError;
import com.ecomerce.notifications.domain.identity.Uuids;
import com.ecomerce.notifications.domain.valueobject.TemplateCodeFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Plantilla de correo que gestiona negocio; posee su historial de versiones.
 */
// Invariante (guardada en el factory y en los métodos de negocio): Como mucho una versión de la plantilla está en active.
// Invariante (guardada en el factory y en los métodos de negocio): Los versionNumber de una plantilla son correlativos desde 1 y nunca se reutilizan.
public class EmailTemplate {

    private UUID id = Uuids.v7();

    private String code;

    private String name;

    private String description;

    // Derivado (recalculado en cada método de negocio): Es true si y solo si alguna versión de la plantilla está en active. Se persiste y se recalcula en la misma transacción que publishTemplateVersion, activateTemplateVersion y retireTemplate.
    private Boolean hasActiveVersion;

    private List<EmailTemplateVersion> versions = new ArrayList<>();

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /** Factory de creación: una plantilla nace vacía, sin versiones y con hasActiveVersion false. */
    public static EmailTemplate create(String code, String name, String description) {
        TemplateCodeFormat.validate(code);
        return new EmailTemplate(Uuids.v7(), code, name, description, false, List.of(), null);
    }

    /** La versión active, si la hay. */
    public Optional<EmailTemplateVersion> activeVersion() {
        return versions.stream().filter(EmailTemplateVersion::isActive).findFirst();
    }

    public Optional<EmailTemplateVersion> findVersion(int versionNumber) {
        return versions.stream().filter(v -> v.getVersionNumber() == versionNumber).findFirst();
    }

    public Optional<EmailTemplateVersion> findVersionById(UUID versionId) {
        return versions.stream().filter(v -> v.getId().equals(versionId)).findFirst();
    }

    /** Siguiente versionNumber: correlativo desde 1, nunca reutilizado. */
    public int nextVersionNumber() {
        return versions.stream().mapToInt(EmailTemplateVersion::getVersionNumber).max().orElse(0) + 1;
    }

    /** Archiva la versión active, si la hay (publish, activate, retire). Devuelve si había una. */
    public boolean archiveActiveVersion() {
        Optional<EmailTemplateVersion> active = activeVersion();
        active.ifPresent(EmailTemplateVersion::archive);
        recomputeHasActiveVersion();
        return active.isPresent();
    }

    /** Retira la plantilla: archiva su versión active; sin ninguna es TEMPLATE_NOT_PUBLISHED. */
    public void retire() {
        if (!archiveActiveVersion()) {
            throw new TemplateNotPublishedError("La plantilla no tiene ninguna versión active");
        }
    }

    /** Añade una versión nueva (ya active). Como mucho una versión active: la anterior se archivó antes. */
    public void addVersion(EmailTemplateVersion version) {
        if (version.isActive() && activeVersion().isPresent()) {
            throw new InvariantViolatedException("EmailTemplate: como mucho una versión puede estar active.");
        }
        if (version.getVersionNumber() != nextVersionNumber()) {
            throw new InvariantViolatedException("EmailTemplate: los versionNumber son correlativos y no se reutilizan.");
        }
        versions.add(version);
        recomputeHasActiveVersion();
    }

    /** Reactiva una versión archivada; la que estuviera active se archivó antes. */
    public void activateVersion(int versionNumber) {
        EmailTemplateVersion target = findVersion(versionNumber)
                .orElseThrow(() -> new InvariantViolatedException("EmailTemplate: la versión no existe."));
        if (activeVersion().isPresent()) {
            throw new InvariantViolatedException("EmailTemplate: como mucho una versión puede estar active.");
        }
        target.activate();
        recomputeHasActiveVersion();
    }

    private void recomputeHasActiveVersion() {
        this.hasActiveVersion = versions.stream().anyMatch(EmailTemplateVersion::isActive);
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public EmailTemplate(UUID id, String code, String name, String description, Boolean hasActiveVersion, List<EmailTemplateVersion> versions, Long lockVersion) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.hasActiveVersion = hasActiveVersion;
        this.versions = new ArrayList<>(versions);
        this.lockVersion = lockVersion;
    }

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{2,47}$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        for (EmailTemplateVersion child : versions) {
            child.ensureInvariants();
        }
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (code != null && (!CODE_PATTERN.matcher(code).matches())) {
            throw new InvariantViolatedException("EmailTemplate: code queda fuera de sus cotas.");
        }
        if (name != null && (name.codePointCount(0, name.length()) > 120)) {
            throw new InvariantViolatedException("EmailTemplate: name queda fuera de sus cotas.");
        }
        if (description != null && (description.codePointCount(0, description.length()) > 1000)) {
            throw new InvariantViolatedException("EmailTemplate: description queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getHasActiveVersion() {
        return hasActiveVersion;
    }

    public List<EmailTemplateVersion> getVersions() {
        return List.copyOf(versions);
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
