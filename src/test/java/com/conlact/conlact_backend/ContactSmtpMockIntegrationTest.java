package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.entity.ContactMessage;
import com.conlact.conlact_backend.repository.ContactMessageRepository;
import com.conlact.conlact_backend.service.EmailNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContactSmtpMockIntegrationTest extends AdminApiIntegrationSupport {

    @MockitoBean
    private EmailNotificationService emailNotificationService;

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @Test
    @DisplayName("BE-26: POST /api/contacto almacena mensaje y dispara notificación mockeando SMTP (sin envío real)")
    void shouldStoreContactMessageAndTriggerMockedEmailNotification() throws Exception {
        doNothing().when(emailNotificationService).sendContactNotificationToAdmin(any(ContactMessage.class));

        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "Carlos Mendoza",
                "carlos.mendoza@agroexport.com",
                "+593998877665",
                "Consulta de exportación de quesos",
                "Deseamos contactar a las queserías de Chimborazo para alianza de distribución.",
                true
        );

        var response = request(HttpMethod.POST, "/api/contacto", request, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var json = json(response);
        assertThat(json.has("id")).isTrue();
        assertThat(json.path("status").asText()).isEqualTo("received");

        UUID createdId = UUID.fromString(json.path("id").asText());
        var entityOpt = contactMessageRepository.findById(createdId);
        assertThat(entityOpt).isPresent();
        ContactMessage saved = entityOpt.get();
        assertThat(saved.getName()).isEqualTo("Carlos Mendoza");
        assertThat(saved.getEmail()).isEqualTo("carlos.mendoza@agroexport.com");
        assertThat(saved.getIsResolved()).isFalse();

        // Certifica que el envío SMTP fue interceptado/mockeado y no contactó servidores externos
        verify(emailNotificationService, times(1)).sendContactNotificationToAdmin(argThat(msg ->
                msg.getId().equals(createdId) && msg.getEmail().equals("carlos.mendoza@agroexport.com")
        ));
    }

    @Test
    @DisplayName("BE-26: POST /api/contact alias en inglés funciona de forma idéntica")
    void shouldAcceptContactViaEnglishAlias() throws Exception {
        doNothing().when(emailNotificationService).sendContactNotificationToAdmin(any(ContactMessage.class));

        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "Emily Watson",
                "emily.watson@trade.org",
                null,
                "Fair Trade Cooperation",
                "We are interested in your artisanal dairy production in Chimborazo.",
                true
        );

        var response = request(HttpMethod.POST, "/api/contact", request, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var json = json(response);
        UUID createdId = UUID.fromString(json.path("id").asText());
        assertThat(contactMessageRepository.existsById(createdId)).isTrue();
    }

    @Test
    @DisplayName("BE-26: Validaciones de entrada en /api/contacto rechazan payloads mal formados con 400 Bad Request")
    void shouldRejectMalformedContactPayloads() throws Exception {
        // 1. Email inválido
        ContactMessageCreateRequest invalidEmail = new ContactMessageCreateRequest(
                "Juan Pérez",
                "no-es-un-correo-valido",
                "0991234567",
                "Asunto válido",
                "Mensaje con suficiente detalle para pasar longitud mínima.",
                true
        );
        var res1 = request(HttpMethod.POST, "/api/contacto", invalidEmail, null);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 2. Nombre en blanco
        ContactMessageCreateRequest blankName = new ContactMessageCreateRequest(
                "   ",
                "valido@correo.com",
                "0991234567",
                "Asunto válido",
                "Mensaje con suficiente detalle para pasar longitud mínima.",
                true
        );
        var res2 = request(HttpMethod.POST, "/api/contacto", blankName, null);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. Asunto en blanco
        ContactMessageCreateRequest blankSubject = new ContactMessageCreateRequest(
                "Juan Pérez",
                "valido@correo.com",
                "0991234567",
                "  ",
                "Mensaje con suficiente detalle para pasar longitud mínima.",
                true
        );
        var res3 = request(HttpMethod.POST, "/api/contacto", blankSubject, null);
        assertThat(res3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 4. Mensaje en blanco
        ContactMessageCreateRequest blankMessage = new ContactMessageCreateRequest(
                "Juan Pérez",
                "valido@correo.com",
                "0991234567",
                "Asunto válido",
                "   ",
                true
        );
        var res4 = request(HttpMethod.POST, "/api/contacto", blankMessage, null);
        assertThat(res4.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("BE-26: Flujo administrativo de resolución de mensajes de contacto")
    void shouldAllowAdminToManageAndResolveMessages() throws Exception {
        // Sembrar un mensaje directo
        ContactMessage msg = contactMessageRepository.save(ContactMessage.builder()
                .name("Cooperativa Norte")
                .email("info@coopnorte.ec")
                .subject("Solicitud de visita técnica")
                .message("Queremos coordinar una visita técnica a las queserías asociadas.")
                .privacyAccepted(true)
                .isResolved(false)
                .build());

        // Listar como admin
        var listRes = request(HttpMethod.GET, "/api/admin/contacto", null, token);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        var listJson = json(listRes);
        assertThat(listJson.isArray()).isTrue();
        assertThat(listJson.size()).isGreaterThanOrEqualTo(1);

        // Resolver mensaje
        var resolveRes = request(HttpMethod.PATCH, "/api/admin/contacto/" + msg.getId() + "/resolver", null, token);
        assertThat(resolveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        var resolveJson = json(resolveRes);
        assertThat(resolveJson.path("is_resolved").asBoolean()).isTrue();

        // Verificar en base de datos
        ContactMessage updated = contactMessageRepository.findById(msg.getId()).orElseThrow();
        assertThat(updated.getIsResolved()).isTrue();
    }
}
