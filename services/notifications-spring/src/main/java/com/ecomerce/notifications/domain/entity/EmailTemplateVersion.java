package com.ecomerce.notifications.domain.entity;

import com.ecomerce.notifications.domain.enums.TemplateVersionStatus;
import com.ecomerce.notifications.domain.errors.DuplicateTemplateVariablesError;
import com.ecomerce.notifications.domain.errors.InvalidStateTransitionException;
import com.ecomerce.notifications.domain.errors.InvariantViolatedException;
import com.ecomerce.notifications.domain.errors.MalformedTemplateMarkerError;
import com.ecomerce.notifications.domain.errors.UndeclaredTemplateVariablesError;
import com.ecomerce.notifications.domain.identity.Uuids;
import com.ecomerce.notifications.domain.valueobject.TemplateMarkers;
import com.ecomerce.notifications.domain.valueobject.TemplateVariable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Contenido inmutable de una plantilla en un momento dado. Solo cambia su status.
 */
// Invariante (guardada en el factory y en los métodos de negocio): subject, htmlBody, textBody y variables no cambian tras crear la versión (no hay setters).
// Invariante (factory y activate): toda aparición de {{ en subject, htmlBody o textBody abre un marcador {{name}} bien formado (la triple llave {{{name}}} no lo es). Rige desde la v0.2.0; las versiones anteriores que no lo cumplan no se pueden reactivar.
// Invariante (factory): todo marcador {{name}} de subject, htmlBody o textBody nombra una variable declarada en variables.
// Invariante (factory): los nombres de variables son únicos dentro de la versión.
public class EmailTemplateVersion {

    private UUID id = Uuids.v7();

    private Integer versionNumber;

    private String subject;

    private String htmlBody;

    private String textBody;

    private List<TemplateVariable> variables = new ArrayList<>();

    private TemplateVersionStatus status = TemplateVersionStatus.ACTIVE;

    private Instant publishedAt = Instant.now();

    /**
     * Factory de creación: la versión nace active y con su contenido ya validado, en el orden del diseño:
     * nombres repetidos, marcadores mal formados y marcadores sin declarar.
     */
    public static EmailTemplateVersion create(int versionNumber, String subject, String htmlBody, String textBody,
            List<TemplateVariable> variables) {
        List<TemplateVariable> declared = variables == null ? List.of() : variables;
        Set<String> names = new LinkedHashSet<>();
        Set<String> repeated = new TreeSet<>();
        for (TemplateVariable variable : declared) {
            if (!names.add(variable.name())) {
                repeated.add(variable.name());
            }
        }
        if (!repeated.isEmpty()) {
            throw new DuplicateTemplateVariablesError("variables declara más de una vez: " + String.join(", ", repeated));
        }
        List<TemplateMarkers.Scan> scans = List.of(TemplateMarkers.scan(subject), TemplateMarkers.scan(htmlBody),
                TemplateMarkers.scan(textBody));
        for (TemplateMarkers.Scan scan : scans) {
            if (!scan.wellFormed()) {
                throw new MalformedTemplateMarkerError("Hay un {{ que no abre un marcador {{name}} bien formado");
            }
        }
        Set<String> undeclared = new TreeSet<>();
        for (TemplateMarkers.Scan scan : scans) {
            for (String name : scan.names()) {
                if (!names.contains(name)) {
                    undeclared.add(name);
                }
            }
        }
        if (!undeclared.isEmpty()) {
            throw new UndeclaredTemplateVariablesError("Variables usadas y no declaradas: " + String.join(", ", undeclared));
        }
        return new EmailTemplateVersion(Uuids.v7(), versionNumber, subject, htmlBody, textBody, declared,
                TemplateVersionStatus.ACTIVE, Instant.now());
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public EmailTemplateVersion(UUID id, Integer versionNumber, String subject, String htmlBody, String textBody, List<TemplateVariable> variables, TemplateVersionStatus status, Instant publishedAt) {
        this.id = id;
        this.versionNumber = versionNumber;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
        this.variables = new ArrayList<>(variables);
        this.status = status;
        this.publishedAt = publishedAt;
    }

    // Transiciones válidas del lifecycle del diseño; un estado con Set.of() es terminal.
    private static final Map<TemplateVersionStatus, Set<TemplateVersionStatus>> TRANSITIONS = Map.of(
        TemplateVersionStatus.ACTIVE, Set.of(TemplateVersionStatus.ARCHIVED),
        TemplateVersionStatus.ARCHIVED, Set.of(TemplateVersionStatus.ACTIVE)
    );

    /** ACTIVE → ARCHIVED (publishTemplateVersion, activateTemplateVersion, retireTemplate). */
    public void archive() {
        transitionTo(TemplateVersionStatus.ARCHIVED);
    }

    /**
     * ARCHIVED → ACTIVE (activateTemplateVersion). Una versión que no cumple la regla de marcadores bien
     * formados (solo puede ser una publicada antes de la v0.2.0) no se reactiva.
     */
    public void activate() {
        ensureWellFormedMarkers();
        transitionTo(TemplateVersionStatus.ACTIVE);
    }

    /** Lanza MALFORMED_TEMPLATE_MARKER si algún {{ de subject, htmlBody o textBody no abre un marcador bien formado. */
    public void ensureWellFormedMarkers() {
        if (!TemplateMarkers.scan(subject).wellFormed() || !TemplateMarkers.scan(htmlBody).wellFormed()
                || !TemplateMarkers.scan(textBody).wellFormed()) {
            throw new MalformedTemplateMarkerError("La versión contiene un {{ que no abre un marcador {{name}} bien formado");
        }
    }

    public boolean isActive() {
        return status == TemplateVersionStatus.ACTIVE;
    }

    // Guard interno del lifecycle: lo llaman los métodos semánticos, nunca un handler.
    private void transitionTo(TemplateVersionStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        this.status = target;
    }

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (subject != null && (subject.codePointCount(0, subject.length()) > 300)) {
            throw new InvariantViolatedException("EmailTemplateVersion: subject queda fuera de sus cotas.");
        }
        if (htmlBody != null && (htmlBody.codePointCount(0, htmlBody.length()) > 262144)) {
            throw new InvariantViolatedException("EmailTemplateVersion: htmlBody queda fuera de sus cotas.");
        }
        if (textBody != null && (textBody.codePointCount(0, textBody.length()) > 65536)) {
            throw new InvariantViolatedException("EmailTemplateVersion: textBody queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public List<TemplateVariable> getVariables() {
        return List.copyOf(variables);
    }

    public TemplateVersionStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
