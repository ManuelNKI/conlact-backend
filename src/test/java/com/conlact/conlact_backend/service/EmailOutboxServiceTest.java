package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.email.EmailDeliveryException;
import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.email.TemplatedEmail;
import com.conlact.conlact_backend.entity.EmailOutbox;
import com.conlact.conlact_backend.entity.enums.OutboxStatus;
import com.conlact.conlact_backend.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;

    @Mock
    private IEmailService emailService;

    private EmailOutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new EmailOutboxService(outboxRepository, emailService);
    }

    @Test
    @DisplayName("Outbox: Encolar nuevo correo genera estado PENDING")
    void shouldEnqueueNewEmail() {
        when(outboxRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(outboxRepository.save(any(EmailOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailOutbox result = outboxService.enqueue(
                "cliente@example.com",
                "Asunto prueba",
                EmailTemplate.ORDER_RECEIVED,
                Map.of("customerName", "Carlos", "orderNumber", "1001", "total", "15.00"),
                "key-1"
        );

        assertThat(result.getRecipient()).isEqualTo("cliente@example.com");
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getIdempotencyKey()).isEqualTo("key-1");
        verify(outboxRepository).save(any(EmailOutbox.class));
    }

    @Test
    @DisplayName("Outbox: Idempotencia - No duplicar correo si la clave ya existe")
    void shouldNotDuplicateWhenIdempotencyKeyExists() {
        EmailOutbox existing = EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient("cliente@example.com")
                .subject("Asunto")
                .template(EmailTemplate.ORDER_RECEIVED)
                .idempotencyKey("key-1")
                .status(OutboxStatus.SENT)
                .build();

        when(outboxRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        EmailOutbox result = outboxService.enqueue(
                "cliente@example.com",
                "Asunto",
                EmailTemplate.ORDER_RECEIVED,
                Map.of(),
                "key-1"
        );

        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(outboxRepository, never()).save(any(EmailOutbox.class));
    }

    @Test
    @DisplayName("Outbox: Procesar elemento exitoso lo transiciona a SENT")
    void shouldProcessItemSuccessfully() {
        EmailOutbox item = EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient("cliente@example.com")
                .subject("Asunto prueba")
                .template(EmailTemplate.ORDER_RECEIVED)
                .templateVariables(Map.of("customerName", "Carlos", "orderNumber", "1001", "total", "15.00"))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .build();

        doNothing().when(emailService).deliverDirectly(any(TemplatedEmail.class));

        outboxService.processItem(item);

        assertThat(item.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(item.getSentAt()).isNotNull();
        assertThat(item.getLastError()).isNull();
        verify(outboxRepository).save(item);
    }

    @Test
    @DisplayName("Outbox: Fallo temporal incrementa reintentos y programa backoff")
    void shouldHandleTemporaryFailureWithBackoff() {
        EmailOutbox item = EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient("cliente@example.com")
                .subject("Asunto prueba")
                .template(EmailTemplate.ORDER_RECEIVED)
                .templateVariables(Map.of("customerName", "Carlos", "orderNumber", "1001", "total", "15.00"))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .build();

        doThrow(new EmailDeliveryException("SMTP connection refused"))
                .when(emailService).deliverDirectly(any(TemplatedEmail.class));

        outboxService.processItem(item);

        assertThat(item.getRetryCount()).isEqualTo(1);
        assertThat(item.getNextRetryAt()).isNotNull();
        assertThat(item.getLastError()).contains("EmailDeliveryException");
        verify(outboxRepository).save(item);
    }

    @Test
    @DisplayName("Outbox: Marcar como ABANDONED tras superar el máximo de reintentos")
    void shouldMarkAsAbandonedWhenMaxRetriesExceeded() {
        EmailOutbox item = EmailOutbox.builder()
                .id(UUID.randomUUID())
                .recipient("cliente@example.com")
                .subject("Asunto prueba")
                .template(EmailTemplate.ORDER_RECEIVED)
                .templateVariables(Map.of("customerName", "Carlos", "orderNumber", "1001", "total", "15.00"))
                .status(OutboxStatus.PENDING)
                .retryCount(2)
                .maxRetries(3)
                .build();

        doThrow(new EmailDeliveryException("SMTP auth failed"))
                .when(emailService).deliverDirectly(any(TemplatedEmail.class));

        outboxService.processItem(item);

        assertThat(item.getRetryCount()).isEqualTo(3);
        assertThat(item.getStatus()).isEqualTo(OutboxStatus.ABANDONED);
        verify(outboxRepository).save(item);
    }
}
