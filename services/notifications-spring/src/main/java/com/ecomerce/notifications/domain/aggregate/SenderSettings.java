package com.ecomerce.notifications.domain.aggregate;

import com.ecomerce.notifications.domain.enums.SenderSettingsKey;
import com.ecomerce.notifications.domain.errors.InvariantViolatedException;
import com.ecomerce.notifications.domain.identity.Uuids;
import com.ecomerce.notifications.domain.valueobject.EmailAddressFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Remitente verificado del servicio. Existe como mucho una; mientras no exista no se acepta ninguna petición de envío. Es un buzón de la organización, no de una persona: no es dato personal y no lleva personal; senderName no debe nombrar a un empleado.
 */
// Invariante (guardada en el factory y en los métodos de negocio): Existe como mucho un SenderSettings en el servicio.
public class SenderSettings {

    private UUID id = Uuids.v7();

    // Clave constante que hace imposible un segundo SenderSettings, también ante dos altas simultáneas.
    private SenderSettingsKey key = SenderSettingsKey.DEFAULT;

    // Dirección verificada ante el proveedor de correo.
    private String senderAddress;

    private String senderName;

    private String replyToAddress;

    // Versión de concurrencia optimista del agregado; la gestiona el @Version
    // de la XxxJpa. Null hasta la primera persistencia.
    private Long lockVersion;

    /** Factory de creación: las direcciones se normalizan a minúsculas y se valida su formato. */
    public static SenderSettings create(String senderAddress, String senderName, String replyToAddress) {
        SenderSettings settings = new SenderSettings(Uuids.v7(), SenderSettingsKey.DEFAULT, null, null, null, null);
        settings.replace(senderAddress, senderName, replyToAddress);
        return settings;
    }

    /** Reemplaza los tres campos (uno omitido queda vacío). */
    public void replace(String senderAddress, String senderName, String replyToAddress) {
        String address = lower(senderAddress);
        String replyTo = lower(replyToAddress);
        EmailAddressFormat.validate(address);
        EmailAddressFormat.validate(replyTo);
        this.senderAddress = address;
        this.senderName = senderName;
        this.replyToAddress = replyTo;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    // Rehidratación desde persistencia (lo usa el toDomain del adaptador de repositorio):
    // el estado ya es válido y no se revalida. La creación de negocio va por el factory.
    public SenderSettings(UUID id, SenderSettingsKey key, String senderAddress, String senderName, String replyToAddress, Long lockVersion) {
        this.id = id;
        this.key = key;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.replyToAddress = replyToAddress;
        this.lockVersion = lockVersion;
    }

    private static final Pattern SENDER_ADDRESS_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern REPLY_TO_ADDRESS_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // ─── La guarda del agregado (DSL 2.31) ───────────────────────────────────
    // La emite build y la llama el save del adaptador antes de escribir nada: recalcula los derivados y comprueba las
    // cotas de los campos y los checks del diseño, las hijas primero (el orden decide el code cuando se rompen dos).
    // No la llames desde el handler ni repitas sus comprobaciones en los métodos de negocio.
    public void ensureInvariants() {
        // Las cotas de sus campos (U1, U6: una cota de la entidad es siempre verdad).
        if (senderAddress != null && ((senderAddress.codePointCount(0, senderAddress.length()) > 254) || (!SENDER_ADDRESS_PATTERN.matcher(senderAddress).matches()))) {
            throw new InvariantViolatedException("SenderSettings: senderAddress queda fuera de sus cotas.");
        }
        if (senderName != null && (senderName.codePointCount(0, senderName.length()) > 120)) {
            throw new InvariantViolatedException("SenderSettings: senderName queda fuera de sus cotas.");
        }
        if (replyToAddress != null && ((replyToAddress.codePointCount(0, replyToAddress.length()) > 254) || (!REPLY_TO_ADDRESS_PATTERN.matcher(replyToAddress).matches()))) {
            throw new InvariantViolatedException("SenderSettings: replyToAddress queda fuera de sus cotas.");
        }
    }

    public UUID getId() {
        return id;
    }

    public SenderSettingsKey getKey() {
        return key;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getReplyToAddress() {
        return replyToAddress;
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
