package com.ecomerce.notifications.domain.mail;

import java.util.Set;

/**
 * El proveedor no aceptó el mensaje, o no lo aceptó para TODOS los destinatarios. Envuelve la
 * causa del transporte para que la capa de aplicación no tenga que conocer jakarta.mail.
 *
 * <p><b>Un fallo puede ser parcial</b>, y el caso de uso tiene que poder distinguirlo sin
 * desenvolver la causa: el transporte envía con {@code mail.smtp.sendpartial=true}, así que si el
 * relay rechaza a un destinatario y acepta a otro, el correo SALE para los aceptados y aun así se
 * lanza esta excepción. Tratarla como «no salió» repetiría o daría por fallido un envío que ya
 * llegó. Por eso lleva quién lo recibió ({@link #accepted()}), a quién se rechazó
 * ({@link #rejected()}) y la respuesta del relay ({@link #detail()}). Sin envío parcial (un error
 * de conexión, un rechazo del remitente) {@code accepted()} está vacío.
 */
public class MailDeliveryException extends RuntimeException {

    private final Set<String> accepted;
    private final Set<String> rejected;
    private final String detail;

    public MailDeliveryException(String message, Throwable cause) {
        this(message, cause, Set.of(), Set.of(), cause == null ? null : cause.getMessage());
    }

    public MailDeliveryException(String message, Throwable cause, Set<String> accepted, Set<String> rejected, String detail) {
        super(message, cause);
        this.accepted = accepted == null ? Set.of() : Set.copyOf(accepted);
        this.rejected = rejected == null ? Set.of() : Set.copyOf(rejected);
        this.detail = detail;
    }

    /** Destinatarios a los que el relay SÍ entregó: si no está vacío, el correo salió para ellos. */
    public Set<String> accepted() {
        return accepted;
    }

    /** Destinatarios que el relay rechazó de forma definitiva (un 5xx a su RCPT TO). */
    public Set<String> rejected() {
        return rejected;
    }

    /** ¿El correo salió para alguno? Es la pregunta que decide el desenlace del envío. */
    public boolean partial() {
        return !accepted.isEmpty();
    }

    /** La respuesta del relay, para registrarla junto al desenlace; puede ser null. */
    public String detail() {
        return detail;
    }
}
