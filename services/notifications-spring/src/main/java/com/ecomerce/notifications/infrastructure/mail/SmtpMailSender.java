package com.ecomerce.notifications.infrastructure.mail;

import com.ecomerce.notifications.application.port.out.MailSender;
import com.ecomerce.notifications.domain.mail.MailDeliveryException;
import com.ecomerce.notifications.domain.mail.MailMessage;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Entrega el correo por SMTP. Mismo adaptador en local (contra el Mailpit de
 * infra/) y en producción (contra el proveedor contratado): lo único que cambia son
 * los parámetros de {@code parameters/<perfil>/mail.yaml}, así que cambiar de
 * proveedor es cambiar variables de entorno y reiniciar, con el mismo binario.
 *
 * <p>El saneado del asunto NO está aquí: vive en el constructor de
 * {@link MailMessage}, para que ningún camino pueda saltárselo.
 */
@Component
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;

    /** Cabecera propia con el id del mensaje: su valor es la parte local del Message-ID (los rebotes lo traen de vuelta). */
    private static final String NOTIFICATION_ID_HEADER = "X-Notification-Id";

    /**
     * Crea el MimeMessage. Si el mensaje trae {@code X-Notification-Id}, el Message-ID sale como
     * {@code <id@dominio-del-remitente>}: Jakarta Mail regenera el Message-ID al enviar (updateMessageID), así que
     * fijarlo con setHeader se pierde y hay que sobrescribir ese método. El dominio es despliegue.
     */
    private MimeMessage newMimeMessage(MailMessage message) {
        String id = message.headers().get(NOTIFICATION_ID_HEADER);
        if (!hasText(id) || !(javaMailSender instanceof JavaMailSenderImpl impl)) {
            return javaMailSender.createMimeMessage();
        }
        String messageId = "<" + id + "@" + domainOf(message.from()) + ">";
        return new MimeMessage(impl.getSession()) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
    }

    private static String domainOf(String from) {
        if (from == null) {
            return "localhost";
        }
        String address = from.contains("<") ? from.substring(from.lastIndexOf('<') + 1) : from;
        int at = address.lastIndexOf('@');
        String domain = at < 0 ? "" : address.substring(at + 1).replace(">", "").trim();
        return domain.isEmpty() ? "localhost" : domain;
    }

    public SmtpMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(MailMessage message) {
        String from = message.from();
        if (!hasText(from)) {
            throw new MailDeliveryException(
                    "El mensaje no tiene remitente y el diseño no declara uno de respaldo: no se envía", null);
        }
        if (message.to().isEmpty()) {
            throw new MailDeliveryException("El mensaje no tiene destinatarios: no se envía", null);
        }

        try {
            MimeMessage mime = newMimeMessage(message);
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to().toArray(String[]::new));
            if (!message.cc().isEmpty()) {
                helper.setCc(message.cc().toArray(String[]::new));
            }
            helper.setSubject(nullToEmpty(message.subject()));
            if (hasText(message.replyTo())) {
                helper.setReplyTo(message.replyTo());
            }
            // Cabeceras propias: nombre y valor ya vienen validados y saneados por el
            // constructor de MailMessage.
            for (var header : message.headers().entrySet()) {
                mime.addHeader(header.getKey(), header.getValue());
            }
            // multipart/alternative: el cliente elige. El ORDEN importa —la parte
            // preferida va la última— y MimeMessageHelper#setText(text, html) ya lo
            // respeta.
            helper.setText(nullToEmpty(message.text()), nullToEmpty(message.html()));
            javaMailSender.send(mime);
            log.info("Correo entregado al proveedor: destinatarios={} asunto=\"{}\"",
                    message.to().size(), message.subject());
        } catch (MailException | MessagingException e) {
            // Se envuelve y se relanza: un envío que falla en silencio es un correo
            // que nadie recibe y del que nadie se entera. Pero se dice A QUIÉN: con
            // sendpartial el relay puede haber entregado a unos y rechazado a otros, y
            // eso solo lo sabe el SendFailedException que viaja dentro.
            SendFailedException rejection = sendFailure(e);
            if (rejection == null) {
                throw new MailDeliveryException("El proveedor no aceptó el mensaje", e);
            }
            Set<String> accepted = addresses(rejection.getValidSentAddresses());
            Set<String> rejected = addresses(rejection.getInvalidAddresses());
            String detail = rejection.getNextException() != null
                    ? rejection.getNextException().getMessage()
                    : rejection.getMessage();
            throw new MailDeliveryException(
                    accepted.isEmpty() ? "El proveedor no aceptó el mensaje" : "El proveedor rechazó a parte de los destinatarios",
                    e, accepted, rejected, detail);
        }
    }

    /**
     * El SendFailedException de la cadena de causas, si lo hay. JavaMailSenderImpl lo envuelve
     * en un MailSendException, una entrada por mensaje; aquí se manda uno.
     */
    private static SendFailedException sendFailure(Exception e) {
        if (e instanceof MailSendException send) {
            for (Exception failure : send.getMessageExceptions()) {
                SendFailedException found = sendFailure(failure);
                if (found != null) {
                    return found;
                }
            }
        }
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException failed) {
                return failed;
            }
        }
        return null;
    }

    private static Set<String> addresses(Address[] value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null) {
            for (Address address : value) {
                result.add(address instanceof InternetAddress internet ? internet.getAddress() : address.toString());
            }
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
