package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.contact.AdminContactMessageResponse;
import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.dto.contact.ContactMessageResponse;
import com.conlact.conlact_backend.entity.ContactMessage;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository contactMessageRepository;
    private final EmailNotificationService emailNotificationService;

    /**
     * Procesa la recepción y registro de un mensaje de contacto enviado por clientes o interesados.
     * Persiste el mensaje en la tabla contact_messages y dispara de forma asíncrona la notificación
     * de correo hacia el administrador.
     */
    @Transactional
    public ContactMessageResponse processContactMessage(ContactMessageCreateRequest request) {
        String cleanName = request.name().trim();
        String cleanEmail = request.email().trim().toLowerCase();
        String cleanPhone = (request.phone() != null && !request.phone().isBlank()) ? request.phone().trim() : null;
        String cleanSubject = request.subject().trim();
        String cleanMessage = request.message().trim();
        boolean privacy = Boolean.TRUE.equals(request.privacyAccepted());

        ContactMessage contactMessage = ContactMessage.builder()
                .name(cleanName)
                .email(cleanEmail)
                .phone(cleanPhone)
                .subject(cleanSubject)
                .message(cleanMessage)
                .privacyAccepted(privacy)
                .isResolved(false)
                .build();

        ContactMessage saved = contactMessageRepository.save(contactMessage);
        log.info("Mensaje de contacto persistido exitosamente con ID: {}, remitente: {}", saved.getId(), saved.getEmail());

        // Disparo automático del correo de notificación al administrador
        emailNotificationService.sendContactNotificationToAdmin(saved);

        return ContactMessageResponse.received(saved.getId());
    }

    /**
     * Obtiene el listado de mensajes de contacto para administración y backoffice.
     */
    @Transactional(readOnly = true)
    public List<AdminContactMessageResponse> getContactMessages(Boolean onlyUnresolved) {
        List<ContactMessage> messages = Boolean.TRUE.equals(onlyUnresolved)
                ? contactMessageRepository.findByIsResolvedFalseOrderByCreatedAtDesc()
                : contactMessageRepository.findAllByOrderByCreatedAtDesc();

        return messages.stream()
                .map(AdminContactMessageResponse::fromEntity)
                .toList();
    }

    /**
     * Marca un mensaje de contacto como resuelto o pendiente.
     */
    @Transactional
    public AdminContactMessageResponse updateResolvedStatus(UUID id, boolean resolved) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje de contacto no encontrado con ID: " + id));

        message.setIsResolved(resolved);
        ContactMessage updated = contactMessageRepository.save(message);
        log.info("Mensaje de contacto ID: {} actualizado: is_resolved={}", id, resolved);

        return AdminContactMessageResponse.fromEntity(updated);
    }
}
