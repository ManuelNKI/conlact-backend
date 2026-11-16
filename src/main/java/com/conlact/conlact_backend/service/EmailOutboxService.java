package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.email.TemplatedEmail;
import com.conlact.conlact_backend.entity.EmailOutbox;
import com.conlact.conlact_backend.entity.enums.OutboxStatus;
import com.conlact.conlact_backend.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailOutboxRepository outboxRepository;
    private final IEmailService emailService;

    /**
     * Encola un correo en el outbox persistente de forma idempotente.
     * Si ya existe un registro con la misma clave idempotente, no duplica el envío.
     */
    @Transactional
    public EmailOutbox enqueue(String recipient, String subject, EmailTemplate template,
                              Map<String, Object> variables, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<EmailOutbox> existing = outboxRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                EmailOutbox outbox = existing.get();
                log.info("Correo ya encolado con idempotencyKey '{}' en estado {}", idempotencyKey, outbox.getStatus());
                return outbox;
            }
        }

        EmailOutbox outbox = EmailOutbox.builder()
                .recipient(recipient)
                .subject(subject)
                .template(template)
                .templateVariables(variables != null ? variables : Map.of())
                .idempotencyKey(idempotencyKey)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(OffsetDateTime.now())
                .build();

        EmailOutbox saved = outboxRepository.save(outbox);
        log.info("Correo encolado en outbox ID {} para {} con plantilla {}", saved.getId(), recipient, template);
        return saved;
    }

    /**
     * Proceso programado que procesa los correos pendientes del outbox cada 30 segundos.
     * Aplica reintentos exponenciales y abandona envíos tras superar maxRetries.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void processOutbox() {
        if (!emailService.isEmailDeliveryEnabled()) {
            log.trace("Procesamiento de outbox omitido: servicio de correo deshabilitado");
            return;
        }

        List<EmailOutbox> pendingEmails = outboxRepository.findPendingEmails(
                OutboxStatus.PENDING,
                OffsetDateTime.now(),
                PageRequest.of(0, 10)
        );

        if (pendingEmails.isEmpty()) {
            return;
        }

        log.info("Procesando {} correos pendientes del outbox", pendingEmails.size());

        for (EmailOutbox item : pendingEmails) {
            processItem(item);
        }
    }

    @Transactional
    public void processItem(EmailOutbox item) {
        try {
            TemplatedEmail templatedEmail = new TemplatedEmail(
                    item.getRecipient(),
                    item.getSubject(),
                    item.getTemplate(),
                    item.getTemplateVariables()
            );

            emailService.deliverDirectly(templatedEmail);

            item.setStatus(OutboxStatus.SENT);
            item.setSentAt(OffsetDateTime.now());
            item.setLastError(null);
            outboxRepository.save(item);
            log.info("Correo de outbox {} entregado exitosamente a {}", item.getId(), item.getRecipient());
        } catch (Exception ex) {
            int newRetryCount = item.getRetryCount() + 1;
            item.setRetryCount(newRetryCount);
            // Sanitizar mensaje de error para evitar filtrar credenciales o datos sensibles
            String sanitizedError = ex.getClass().getSimpleName() + ": " +
                    (ex.getMessage() != null ? ex.getMessage().replaceAll("(?i)(password|secret|key)=[^\\s]+", "$1=***") : "Error desconocido");
            item.setLastError(sanitizedError);

            if (newRetryCount >= item.getMaxRetries()) {
                item.setStatus(OutboxStatus.ABANDONED);
                log.error("Correo de outbox {} marcado como ABANDONADO tras {} intentos. Error: {}",
                        item.getId(), newRetryCount, sanitizedError);
            } else {
                // Backoff exponencial: 1 min, 2 min, 4 min...
                long backoffSeconds = (long) Math.pow(2, newRetryCount) * 60;
                item.setNextRetryAt(OffsetDateTime.now().plusSeconds(backoffSeconds));
                log.warn("Fallo temporal al enviar correo de outbox {} (intento {}/{}). Próximo reintento en {}s. Error: {}",
                        item.getId(), newRetryCount, item.getMaxRetries(), backoffSeconds, sanitizedError);
            }
            outboxRepository.save(item);
        }
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return outboxRepository.countByStatus(OutboxStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long getSentCount() {
        return outboxRepository.countByStatus(OutboxStatus.SENT);
    }

    @Transactional(readOnly = true)
    public long getAbandonedCount() {
        return outboxRepository.countByStatus(OutboxStatus.ABANDONED);
    }
}
