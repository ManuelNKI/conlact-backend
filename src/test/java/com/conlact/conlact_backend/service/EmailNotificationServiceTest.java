package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.config.EmailAsyncConfig;
import com.conlact.conlact_backend.email.*;
import com.conlact.conlact_backend.entity.ContactMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(EmailNotificationServiceTest.Config.class)
class EmailNotificationServiceTest {
    @Autowired private IEmailService emails;
    @Autowired @Qualifier("disabledEmails") private IEmailService disabledEmails;
    @Autowired private JavaMailSender sender;

    @Configuration
    @Import(EmailAsyncConfig.class)
    static class Config {
        @Bean JavaMailSender sender() { return mock(JavaMailSender.class); }

        @Bean TemplateEngine templates() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            resolver.setTemplateMode("HTML");
            TemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            return engine;
        }

        @Bean @Primary EmailNotificationService emails(JavaMailSender sender, TemplateEngine templates) {
            return new EmailNotificationService(sender, templates, true, "no-reply@example.com", "CONLAC-T", "admin@example.com");
        }

        @Bean EmailNotificationService disabledEmails(JavaMailSender sender, TemplateEngine templates) {
            return new EmailNotificationService(sender, templates, false, "no-reply@example.com", "CONLAC-T", "admin@example.com");
        }
    }

    @BeforeEach
    void resetSender() {
        reset(sender);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private TemplatedEmail notification(Map<String, Object> variables) {
        return new TemplatedEmail("cliente@example.com", "Información de Pilahuín", EmailTemplate.NOTIFICATION, variables);
    }

    @Test
    @DisplayName("Correo: El envío ocurre en otro hilo y no bloquea al llamador durante SMTP")
    void shouldSendAsynchronously() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> sendingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread().getName());
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("SMTP de prueba no liberado");
            return null;
        }).when(sender).send(any(MimeMessage.class));
        var future = emails.sendEmail(notification(Map.of("title", "Aviso", "message", "Mensaje")));
        try {
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
            assertThat(sendingThread.get()).startsWith("mail-").isNotEqualTo(Thread.currentThread().getName());
        } finally {
            release.countDown();
        }
        future.get(3, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Correo: Plantillas HTML escapan contenido, preservan UTF-8 y copian variables antes de encolar")
    void shouldRenderSafeUtf8Email() throws Exception {
        AtomicReference<MimeMessage> sent = new AtomicReference<>();
        doAnswer(invocation -> { sent.set(invocation.getArgument(0)); return null; }).when(sender).send(any(MimeMessage.class));
        var variables = new HashMap<String, Object>(Map.of("title", "Tradición quesera", "message", "<script>alert(1)</script> & queso",
                "recipientName", "María", "actionUrl", "https://example.com/pedido"));
        var email = notification(variables);
        variables.clear();
        emails.sendEmail(email).get(3, TimeUnit.SECONDS);
        MimeMessage message = sent.get();
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Información de Pilahuín");
        assertThat(message.getContentType()).startsWith("text/html").containsIgnoringCase("charset=UTF-8");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("cliente@example.com");
        assertThat(message.getContent().toString()).contains("María", "Tradición quesera", "&lt;script&gt;", "&amp; queso")
                .doesNotContain("<script>");
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    @DisplayName("Correo: Todas las plantillas del contrato se renderizan y envían como HTML")
    void shouldRenderEveryTemplate(EmailTemplate template) throws Exception {
        var variables = new HashMap<String, Object>();
        template.requiredVariables().forEach(name -> variables.put(name, "Dato de prueba"));
        emails.sendEmail(new TemplatedEmail("cliente@example.com", "Aviso", template, variables)).get(3, TimeUnit.SECONDS);
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Correo: Un fallo SMTP completa el futuro con error observable")
    void shouldPropagateSmtpFailures() {
        doThrow(new MailSendException("SMTP de prueba falló")).when(sender).send(any(MimeMessage.class));
        var future = emails.sendEmail(notification(Map.of("title", "Aviso", "message", "Mensaje")));
        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                .hasCauseInstanceOf(EmailDeliveryException.class).hasRootCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("Correo: Deshabilitado devuelve error y no contacta al servidor SMTP")
    void shouldNotSendWhenDisabled() {
        var future = disabledEmails.sendEmail(notification(Map.of("title", "Aviso", "message", "Mensaje")));
        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(EmailDeliveryException.class);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Correo: Rechaza encabezados, enlaces inseguros y variables incompletas antes de encolar")
    void shouldValidateEmailInput() {
        assertThatThrownBy(() -> new TemplatedEmail("invalid", "Aviso", EmailTemplate.NOTIFICATION, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TemplatedEmail("cliente@example.com", "Aviso\r\nBcc:otro@example.com", EmailTemplate.NOTIFICATION, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notification(Map.of("title", "Aviso"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notification(Map.of("title", "Aviso", "message", "Mensaje", "actionUrl", "javascript:alert(1)")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Correo: Contacto usa el buzón administrativo, HTML escapado y asunto estable")
    void shouldSendContactThroughRealTemplateService() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<MimeMessage> sent = new AtomicReference<>();
        doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            delivered.countDown();
            return null;
        }).when(sender).send(any(MimeMessage.class));
        emails.sendContactNotificationToAdmin(ContactMessage.builder()
                .name("María").email("cliente@example.com").phone("0991234567")
                .subject("<script>" + "x".repeat(193)).message("Consulta sobre Pilahuín").build());
        assertThat(delivered.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(sent.get().getAllRecipients()[0].toString()).isEqualTo("admin@example.com");
        assertThat(sent.get().getSubject()).isEqualTo("[CONLAC-T] Nuevo mensaje de contacto");
        assertThat(sent.get().getContent().toString()).contains("María", "0991234567", "&lt;script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    @DisplayName("Correo: Contactos con envío deshabilitado no acceden al servidor SMTP")
    void shouldSkipContactNotificationWhenDisabled() throws Exception {
        disabledEmails.sendContactNotificationToAdmin(ContactMessage.builder().name("Contacto").build());
        // La siguiente tarea en el mismo executor asegura que el procesamiento asíncrono funciona.
        assertThatThrownBy(() -> disabledEmails.sendEmail(notification(Map.of("title", "Aviso", "message", "Mensaje")))
                .get(3, TimeUnit.SECONDS)).hasRootCauseInstanceOf(EmailDeliveryException.class);
        verify(sender, never()).send(any(MimeMessage.class));
    }
}
