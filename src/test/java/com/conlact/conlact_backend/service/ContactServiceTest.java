package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.contact.AdminContactMessageResponse;
import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.dto.contact.ContactMessageResponse;
import com.conlact.conlact_backend.entity.ContactMessage;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ContactMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private IEmailService emailNotificationService;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(contactMessageRepository, emailNotificationService);
    }

    @Test
    @DisplayName("BE-25: Procesa y persiste mensaje de contacto, disparando notificación por correo al admin")
    void testProcessContactMessage_Success() {
        UUID generatedId = UUID.randomUUID();

        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(invocation -> {
            ContactMessage entity = invocation.getArgument(0);
            entity.setId(generatedId);
            entity.setCreatedAt(OffsetDateTime.now());
            return entity;
        });

        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "  María Morales  ",
                "  MARIA.MORALES@EMPRESA.EC  ",
                "  +593984561234  ",
                "  Cotización institucional  ",
                "  Deseamos cotizar 200 unidades de queso fresco.  ",
                true
        );

        ContactMessageResponse response = contactService.processContactMessage(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.status()).isEqualTo("received");
        assertThat(response.message()).contains("Mensaje recibido correctamente");

        ArgumentCaptor<ContactMessage> captor = ArgumentCaptor.forClass(ContactMessage.class);
        verify(contactMessageRepository).save(captor.capture());
        ContactMessage saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("María Morales");
        assertThat(saved.getEmail()).isEqualTo("maria.morales@empresa.ec");
        assertThat(saved.getPhone()).isEqualTo("+593984561234");
        assertThat(saved.getSubject()).isEqualTo("Cotización institucional");
        assertThat(saved.getMessage()).isEqualTo("Deseamos cotizar 200 unidades de queso fresco.");
        assertThat(saved.getPrivacyAccepted()).isTrue();
        assertThat(saved.getIsResolved()).isFalse();

        verify(emailNotificationService).sendContactNotificationToAdmin(argThat(message ->
                message != saved && generatedId.equals(message.getId())
                        && saved.getEmail().equals(message.getEmail())
                        && saved.getMessage().equals(message.getMessage())));
    }

    private ContactMessageCreateRequest validRequest() {
        return new ContactMessageCreateRequest("María", "maria@example.com", null,
                "Consulta", "Consulta de productos artesanales", true);
    }

    @Test
    @DisplayName("Contacto: La notificación se encola únicamente después del commit")
    void shouldNotifyOnlyAfterCommit() {
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            contactService.processContactMessage(validRequest());
            verifyNoInteractions(emailNotificationService);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailNotificationService).sendContactNotificationToAdmin(any(ContactMessage.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Contacto: Una transacción revertida no dispara correos")
    void shouldNotNotifyOnRollback() {
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            contactService.processContactMessage(validRequest());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(emailNotificationService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Contacto: Un rechazo de la cola de correo conserva la confirmación del mensaje guardado")
    void shouldPreserveContactResponseWhenMailQueueRejectsTask() {
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new TaskRejectedException("Cola de prueba llena"))
                .when(emailNotificationService).sendContactNotificationToAdmin(any(ContactMessage.class));
        assertThat(contactService.processContactMessage(validRequest()).status()).isEqualTo("received");
        verify(contactMessageRepository).save(any(ContactMessage.class));
    }

    @Test
    @DisplayName("BE-25: Consulta administrativa de mensajes filtra correctamente entre pendientes y todos")
    void testGetContactMessages_Filters() {
        ContactMessage msg1 = ContactMessage.builder()
                .id(UUID.randomUUID())
                .name("Carlos")
                .email("carlos@mail.com")
                .subject("Consulta")
                .message("Texto")
                .isResolved(false)
                .createdAt(OffsetDateTime.now())
                .build();

        ContactMessage msg2 = ContactMessage.builder()
                .id(UUID.randomUUID())
                .name("Ana")
                .email("ana@mail.com")
                .subject("Duda")
                .message("Texto")
                .isResolved(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(contactMessageRepository.findByIsResolvedFalseOrderByCreatedAtDesc()).thenReturn(List.of(msg1));
        when(contactMessageRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(msg1, msg2));

        List<AdminContactMessageResponse> unresolved = contactService.getContactMessages(true);
        assertThat(unresolved).hasSize(1);
        assertThat(unresolved.get(0).name()).isEqualTo("Carlos");

        List<AdminContactMessageResponse> all = contactService.getContactMessages(false);
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("BE-25: Marca un mensaje de contacto como resuelto exitosamente")
    void testUpdateResolvedStatus_Success() {
        UUID id = UUID.randomUUID();
        ContactMessage existing = ContactMessage.builder()
                .id(id)
                .name("Carlos")
                .email("carlos@mail.com")
                .subject("Consulta")
                .message("Texto")
                .isResolved(false)
                .createdAt(OffsetDateTime.now())
                .build();

        when(contactMessageRepository.findById(id)).thenReturn(Optional.of(existing));
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));

        AdminContactMessageResponse response = contactService.updateResolvedStatus(id, true);

        assertThat(response.isResolved()).isTrue();
        verify(contactMessageRepository).save(existing);
    }

    @Test
    @DisplayName("BE-25: Actualizar estado de mensaje inexistente lanza ResourceNotFoundException (404)")
    void testUpdateResolvedStatus_NotFound() {
        UUID missingId = UUID.randomUUID();
        when(contactMessageRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.updateResolvedStatus(missingId, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Mensaje de contacto no encontrado");
    }
}
