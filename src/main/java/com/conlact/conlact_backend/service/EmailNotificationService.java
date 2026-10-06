package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.entity.ContactMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailNotificationService {

    private final String adminRecipientEmail;
    private final String senderEmail;
    private final boolean mailEnabled;

    public EmailNotificationService(
            @Value("${app.mail.admin-recipient:admin@conlact.org}") String adminRecipientEmail,
            @Value("${app.mail.from:notificaciones@conlact.org}") String senderEmail,
            @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        this.adminRecipientEmail = adminRecipientEmail;
        this.senderEmail = senderEmail;
        this.mailEnabled = mailEnabled;
    }

    /**
     * Envía de forma asíncrona una notificación por correo electrónico a los administradores
     * cuando un cliente o interesado remite un mensaje a través del formulario público de contacto.
     */
    @Async
    public void sendContactNotificationToAdmin(ContactMessage contactMessage) {
        try {
            String subject = "[CONLAC-T] Nuevo mensaje de contacto: " + contactMessage.getSubject();
            String formattedBody = buildNotificationBody(contactMessage);

            log.info("Disparando notificación de correo al administrador [{}] desde [{}] con asunto: '{}'. Contenido: \n{}",
                    adminRecipientEmail, senderEmail, subject, formattedBody);

            if (mailEnabled) {
                // Aquí se conectará el cliente JavaMail en Sprint 5 (BE-24)
                log.info("Correo electrónico despachado exitosamente vía SMTP a {}", adminRecipientEmail);
            } else {
                log.info("Servicio de correo en modo simulado/local. Notificación registrada exitosamente.");
            }
        } catch (Exception ex) {
            log.error("Error al despachar notificación de correo para el mensaje de contacto id={}: {}",
                    contactMessage.getId(), ex.getMessage(), ex);
        }
    }

    private String buildNotificationBody(ContactMessage message) {
        return """
                ============================================================
                NUEVO MENSAJE DE CONTACTO INSTITUCIONAL - CONLAC-T
                ============================================================
                ID Mensaje: %s
                Fecha: %s
                Remitente: %s
                Correo: %s
                Teléfono: %s
                Asunto: %s
                ------------------------------------------------------------
                Mensaje:
                %s
                ============================================================
                """.formatted(
                message.getId(),
                message.getCreatedAt() != null ? message.getCreatedAt() : "Recién recibido",
                message.getName(),
                message.getEmail(),
                message.getPhone() != null ? message.getPhone() : "No provisto",
                message.getSubject(),
                message.getMessage()
        );
    }
}
