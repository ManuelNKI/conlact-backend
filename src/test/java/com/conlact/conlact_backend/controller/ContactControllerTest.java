package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.dto.contact.ContactMessageResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.service.ContactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ContactController contactController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contactController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("BE-25: POST /api/contacto (ruta español) procesa formulario y retorna 201 Created")
    void testSubmitContactMessage_SpanishRoute_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        ContactMessageResponse mockResponse = ContactMessageResponse.received(messageId);

        when(contactService.processContactMessage(any(ContactMessageCreateRequest.class))).thenReturn(mockResponse);

        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "María Morales",
                "maria.morales@empresa.ec",
                "+593984561234",
                "Pedido institucional al por mayor",
                "Deseamos cotizar 200 unidades de queso fresco semanalmente.",
                true
        );

        mockMvc.perform(post("/api/contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(messageId.toString()))
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.message").value("Mensaje recibido correctamente. Un asesor del consorcio se comunicará a la brevedad."));
    }

    @Test
    @DisplayName("BE-25: POST /api/contact (alias inglés) procesa formulario y retorna 201 Created")
    void testSubmitContactMessage_EnglishRoute_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        ContactMessageResponse mockResponse = ContactMessageResponse.received(messageId);

        when(contactService.processContactMessage(any(ContactMessageCreateRequest.class))).thenReturn(mockResponse);

        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "John Doe",
                "john.doe@example.com",
                null,
                "General Inquiry",
                "I would like more information about your dairy products.",
                false
        );

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(messageId.toString()))
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    @DisplayName("BE-25: POST /api/contacto con nombre en blanco retorna 400 Bad Request por validación")
    void testSubmitContactMessage_BlankName_BadRequest() throws Exception {
        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "",
                "maria@mail.com",
                null,
                "Asunto",
                "Mensaje válido",
                true
        );

        mockMvc.perform(post("/api/contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("BE-25: POST /api/contacto con email inválido retorna 400 Bad Request por validación")
    void testSubmitContactMessage_InvalidEmail_BadRequest() throws Exception {
        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "María Morales",
                "correo-invalido-sin-arroba",
                null,
                "Asunto",
                "Mensaje válido",
                true
        );

        mockMvc.perform(post("/api/contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("BE-25: POST /api/contacto con mensaje vacío retorna 400 Bad Request por validación")
    void testSubmitContactMessage_BlankMessage_BadRequest() throws Exception {
        ContactMessageCreateRequest request = new ContactMessageCreateRequest(
                "María Morales",
                "maria@mail.com",
                null,
                "Asunto",
                "   ",
                true
        );

        mockMvc.perform(post("/api/contacto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.message").exists());
    }
}
