package com.conlact.conlact_backend.event;

import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.service.EmailOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class EmailEventListener {

    private final EmailOutboxService outboxService;
    private final String adminRecipient;

    public EmailEventListener(
            EmailOutboxService outboxService,
            @Value("${app.mail.admin-recipient:${app.mail.from:no-reply@conlact.local}}") String adminRecipient
    ) {
        this.outboxService = outboxService;
        this.adminRecipient = adminRecipient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderEmailEvent(OrderEmailEvent event) {
        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.info("Envío de correo omitido para pedido #{}: no se proporcionó dirección de correo", event.getOrderNumber());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("customerName", event.getCustomerName());
        variables.put("orderNumber", event.getOrderNumber());

        if (event.isNewOrder()) {
            variables.put("total", event.getTotal());
            if (event.getActionUrl() != null) {
                variables.put("actionUrl", event.getActionUrl());
            }
            String subject = "[CONLAC-T] Pedido recibido #" + event.getOrderNumber();
            String idempotencyKey = "order-received-" + event.getOrderId();
            outboxService.enqueue(event.getCustomerEmail(), subject, EmailTemplate.ORDER_RECEIVED, variables, idempotencyKey);
        } else {
            variables.put("status", event.getStatus());
            if (event.getActionUrl() != null) {
                variables.put("actionUrl", event.getActionUrl());
            }
            String subject = "[CONLAC-T] Actualización de pedido #" + event.getOrderNumber();
            String idempotencyKey = "order-status-" + event.getOrderId() + "-" + event.getStatus();
            outboxService.enqueue(event.getCustomerEmail(), subject, EmailTemplate.ORDER_STATUS, variables, idempotencyKey);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContactEmailEvent(ContactEmailEvent event) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("contactName", event.getContactName());
        variables.put("contactEmail", event.getContactEmail());
        variables.put("message", event.getMessage());
        if (event.getSubject() != null) {
            variables.put("contactSubject", event.getSubject());
        }
        if (event.getPhone() != null) {
            variables.put("phone", event.getPhone());
        }

        String subject = "[CONLAC-T] Nuevo mensaje de contacto";
        String idempotencyKey = "contact-msg-" + event.getContactMessageId();
        outboxService.enqueue(adminRecipient, subject, EmailTemplate.CONTACT_NOTIFICATION, variables, idempotencyKey);
    }
}
