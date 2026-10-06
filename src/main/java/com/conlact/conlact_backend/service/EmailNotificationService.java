package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.email.EmailDeliveryException;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.email.TemplatedEmail;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class EmailNotificationService implements IEmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final boolean enabled;
    private final String senderAddress;
    private final String senderName;

    public EmailNotificationService(JavaMailSender mailSender, TemplateEngine templateEngine,
                                    @Value("${app.mail.enabled:false}") boolean enabled,
                                    @Value("${app.mail.from:no-reply@conlact.local}") String senderAddress,
                                    @Value("${app.mail.sender-name:CONLAC-T}") String senderName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.enabled = enabled;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
    }

    @Override
    @Async("mailTaskExecutor")
    public CompletableFuture<Void> sendEmail(TemplatedEmail email) {
        Objects.requireNonNull(email, "El correo es obligatorio");
        if (!enabled) {
            throw new EmailDeliveryException("El envío de correos está deshabilitado. Configure SMTP y MAIL_ENABLED");
        }
        try {
            Context context = new Context(Locale.forLanguageTag("es-EC"));
            context.setVariables(email.variables());
            String html = templateEngine.process(email.template().templateName(), context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress, senderName);
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Correo enviado mediante plantilla {}", email.template());
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.error("Falló el envío con plantilla {}: {}", email.template(), ex.getClass().getSimpleName());
            throw new EmailDeliveryException("No se pudo enviar el correo", ex);
        }
    }
}
