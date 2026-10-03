package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.image.*;
import com.conlact.conlact_backend.entity.enums.AssociationImageType;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.service.AssociationImageService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AssociationImageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssociationImageService associationImageService;

    @InjectMocks
    private AssociationImageController associationImageController;

    private ObjectMapper objectMapper;
    private UUID associationId;
    private UUID imageId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(associationImageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        associationId = UUID.randomUUID();
        imageId = UUID.randomUUID();
    }

    @Test
    @DisplayName("TC-BE14-13: GET /api/associations/{id}/fotos retorna 200 y galería ordenada")
    void testGetGallerySuccess() throws Exception {
        AssociationGalleryResponse response = AssociationGalleryResponse.builder()
                .associationId(associationId)
                .images(List.of(
                        AssociationImageItemResponse.builder()
                                .id(imageId)
                                .imageType(AssociationImageType.facility)
                                .url("https://example.com/foto.jpg")
                                .altText("Instalaciones")
                                .sortOrder(1)
                                .build()
                ))
                .build();

        when(associationImageService.getPublishedAssociationImages(associationId)).thenReturn(response);

        mockMvc.perform(get("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.association_id").value(associationId.toString()))
                .andExpect(jsonPath("$.images[0].url").value("https://example.com/foto.jpg"))
                .andExpect(jsonPath("$.images[0].image_type").value("facility"))
                .andExpect(jsonPath("$.images[0].sort_order").value(1));
    }

    @Test
    @DisplayName("TC-BE14-01 - TC-BE14-04: POST /api/associations/{id}/fotos registra foto válida y retorna 201")
    void testCreateImageSuccess() throws Exception {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/productor.webp")
                .imageType(AssociationImageType.producer)
                .altText("Productores")
                .sortOrder(2)
                .build();

        AssociationImageItemResponse response = AssociationImageItemResponse.builder()
                .id(imageId)
                .imageType(AssociationImageType.producer)
                .url("https://example.com/productor.webp")
                .altText("Productores")
                .sortOrder(2)
                .build();

        when(associationImageService.createAssociationImage(eq(associationId), any(CreateAssociationImageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(imageId.toString()))
                .andExpect(jsonPath("$.url").value("https://example.com/productor.webp"))
                .andExpect(jsonPath("$.image_type").value("producer"))
                .andExpect(jsonPath("$.sort_order").value(2));
    }

    @Test
    @DisplayName("TC-BE14-05 - TC-BE14-08: POST /api/associations/{id}/fotos con URL inválida o no permitida retorna 400")
    void testCreateImageInvalidUrlBadRequest() throws Exception {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("http://example.com/insegura.pdf")
                .imageType(AssociationImageType.seal)
                .sortOrder(1)
                .build();

        mockMvc.perform(post("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.url").exists());
    }

    @Test
    @DisplayName("TC-BE14-10: POST /api/associations/{id}/fotos con sort_order < 0 retorna 400")
    void testCreateImageNegativeSortOrderBadRequest() throws Exception {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.facility)
                .sortOrder(-5)
                .build();

        mockMvc.perform(post("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.sortOrder").exists());
    }

    @Test
    @DisplayName("TC-BE14-11: POST para asociación inexistente retorna 404")
    void testCreateImageAssociationNotFound() throws Exception {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.facility)
                .sortOrder(1)
                .build();

        when(associationImageService.createAssociationImage(eq(associationId), any(CreateAssociationImageRequest.class)))
                .thenThrow(new ResourceNotFoundException("Asociación no encontrada con id: " + associationId));

        mockMvc.perform(post("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("TC-BE14-12: POST con URL duplicada en la misma asociación retorna 409 Conflict")
    void testCreateImageDuplicateUrlConflict() throws Exception {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.facility)
                .sortOrder(1)
                .build();

        when(associationImageService.createAssociationImage(eq(associationId), any(CreateAssociationImageRequest.class)))
                .thenThrow(new ConflictException("La URL ya se encuentra vinculada a esta asociación"));

        mockMvc.perform(post("/api/associations/{associationId}/fotos", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("TC-BE14-14 & TC-BE14-15: PATCH actualiza foto y retorna 200")
    void testUpdateImageSuccess() throws Exception {
        UpdateAssociationImageRequest request = UpdateAssociationImageRequest.builder()
                .url("https://example.com/foto-actualizada.png")
                .sortOrder(3)
                .build();

        AssociationImageItemResponse response = AssociationImageItemResponse.builder()
                .id(imageId)
                .imageType(AssociationImageType.facility)
                .url("https://example.com/foto-actualizada.png")
                .sortOrder(3)
                .build();

        when(associationImageService.updateAssociationImage(eq(associationId), eq(imageId), any(UpdateAssociationImageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/associations/{associationId}/fotos/{imageId}", associationId, imageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/foto-actualizada.png"))
                .andExpect(jsonPath("$.sort_order").value(3));
    }

    @Test
    @DisplayName("TC-BE14-17: DELETE /api/associations/{id}/fotos/{imageId} retorna 204 No Content")
    void testDeleteImageSuccess() throws Exception {
        doNothing().when(associationImageService).deleteAssociationImage(associationId, imageId);

        mockMvc.perform(delete("/api/associations/{associationId}/fotos/{imageId}", associationId, imageId))
                .andExpect(status().isNoContent());

        verify(associationImageService, times(1)).deleteAssociationImage(associationId, imageId);
    }

    @Test
    @DisplayName("TC-BE14-16: PATCH /api/associations/{id}/fotos/orden reordena galería y retorna 200")
    void testReorderImagesSuccess() throws Exception {
        ReorderAssociationImagesRequest request = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(imageId, 1)
                ))
                .build();

        AssociationGalleryResponse response = AssociationGalleryResponse.builder()
                .associationId(associationId)
                .images(List.of(
                        AssociationImageItemResponse.builder()
                                .id(imageId)
                                .imageType(AssociationImageType.facility)
                                .url("https://example.com/foto.jpg")
                                .sortOrder(1)
                                .build()
                ))
                .build();

        when(associationImageService.reorderAssociationImages(eq(associationId), any(ReorderAssociationImagesRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/associations/{associationId}/fotos/orden", associationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.association_id").value(associationId.toString()))
                .andExpect(jsonPath("$.images[0].sort_order").value(1));
    }
}
