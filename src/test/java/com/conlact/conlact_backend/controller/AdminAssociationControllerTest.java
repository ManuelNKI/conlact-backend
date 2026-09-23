package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.AssociationCreateRequest;
import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.dto.association.AssociationUpdateRequest;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.service.AssociationService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminAssociationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AssociationService associationService;

    @InjectMocks
    private AdminAssociationController adminAssociationController;

    private UUID testId;
    private AssociationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminAssociationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testId = UUID.randomUUID();
        sampleResponse = AssociationResponse.builder()
                .id(testId)
                .name("Asociación El Lindero")
                .slug("asociacion-el-lindero")
                .shortDescription("Productores queseros")
                .isPublished(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/admin/associations retorna 200 y lista de asociaciones")
    void testGetAllAssociations() throws Exception {
        when(associationService.getAllAssociations()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/admin/associations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Asociación El Lindero"))
                .andExpect(jsonPath("$[0].is_published").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/associations con body válido retorna 201 y crea la asociación")
    void testCreateAssociation_Success() throws Exception {
        AssociationCreateRequest request = AssociationCreateRequest.builder()
                .name("Asociación Nueva")
                .shortDescription("Descripción")
                .isPublished(true)
                .build();

        when(associationService.createAssociation(any(AssociationCreateRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/admin/associations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Asociación El Lindero"));
    }

    @Test
    @DisplayName("POST /api/admin/associations con nombre vacío retorna 400 Bad Request por Bean Validation")
    void testCreateAssociation_ValidationError() throws Exception {
        AssociationCreateRequest invalidRequest = AssociationCreateRequest.builder()
                .name("") // Invalido: @NotBlank
                .build();

        mockMvc.perform(post("/api/admin/associations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("PUT /api/admin/associations/{id} actualiza información y retorna 200 OK")
    void testUpdateAssociation_Success() throws Exception {
        AssociationUpdateRequest request = AssociationUpdateRequest.builder()
                .name("Nombre Actualizado")
                .isPublished(true)
                .build();

        when(associationService.updateAssociation(eq(testId), any(AssociationUpdateRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/admin/associations/" + testId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Asociación El Lindero"));
    }

    @Test
    @DisplayName("DELETE /api/admin/associations/{id} ejecuta baja lógica y retorna 204 No Content")
    void testSoftDeleteAssociation_Success() throws Exception {
        doNothing().when(associationService).softDeleteAssociation(testId);

        mockMvc.perform(delete("/api/admin/associations/" + testId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/admin/associations/{id}/restore reactiva la asociación y retorna 200 OK")
    void testRestoreAssociation_Success() throws Exception {
        when(associationService.restoreAssociation(testId)).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/admin/associations/" + testId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_published").value(true));
    }
}
