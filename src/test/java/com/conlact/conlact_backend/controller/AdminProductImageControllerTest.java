package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.product.image.ProductImageCreateRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderItemRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.service.AdminProductService;
import com.conlact.conlact_backend.service.ProductImageService;
import com.conlact.conlact_backend.service.ProductVariantService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminProductImageControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AdminProductService adminProductService;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private AdminProductController controller;

    private UUID productId;
    private UUID imageId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        productId = UUID.randomUUID();
        imageId = UUID.randomUUID();
    }

    private ProductImageResponse mockResponse(UUID id, int sortOrder, boolean isPrimary) {
        return ProductImageResponse.builder()
                .id(id)
                .productId(productId)
                .storagePath("products/frontal.webp")
                .url("https://supabase.co/frontal.webp")
                .altText("Foto frontal")
                .sortOrder(sortOrder)
                .isPrimary(isPrimary)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("BE-20: POST /api/admin/productos/{id}/imagenes asocia una foto y retorna 201 Created")
    void testAddProductImageSpanishRoute() throws Exception {
        ProductImageCreateRequest req = new ProductImageCreateRequest("products/frontal.webp", "Foto frontal", 0, true);
        ProductImageResponse res = mockResponse(imageId, 0, true);

        when(productImageService.addProductImage(eq(productId), any(ProductImageCreateRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/admin/productos/{id}/imagenes", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(imageId.toString()))
                .andExpect(jsonPath("$.storage_path").value("products/frontal.webp"))
                .andExpect(jsonPath("$.sort_order").value(0))
                .andExpect(jsonPath("$.is_primary").value(true));
    }

    @Test
    @DisplayName("BE-20: POST /api/admin/products/{id}/images alias en inglés retorna 201 Created")
    void testAddProductImageEnglishRoute() throws Exception {
        ProductImageCreateRequest req = new ProductImageCreateRequest("products/lateral.webp", "Foto lateral", 1, false);
        ProductImageResponse res = mockResponse(imageId, 1, false);

        when(productImageService.addProductImage(eq(productId), any(ProductImageCreateRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/admin/products/{id}/images", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sort_order").value(1))
                .andExpect(jsonPath("$.is_primary").value(false));
    }

    @Test
    @DisplayName("BE-20: POST con storage_path vacío retorna 400 Bad Request por validación Bean")
    void testAddProductImageValidationErrorEmptyPath() throws Exception {
        ProductImageCreateRequest req = new ProductImageCreateRequest("", "Foto sin ruta", 0, false);

        mockMvc.perform(post("/api/admin/productos/{id}/imagenes", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.storagePath").exists());
    }

    @Test
    @DisplayName("BE-20: POST con sort_order negativo retorna 400 Bad Request")
    void testAddProductImageValidationErrorNegativeSortOrder() throws Exception {
        ProductImageCreateRequest req = new ProductImageCreateRequest("products/foto.webp", "Foto", -1, false);

        mockMvc.perform(post("/api/admin/productos/{id}/imagenes", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.sortOrder").exists());
    }

    @Test
    @DisplayName("BE-20: GET /api/admin/productos/{id}/imagenes lista la galería y retorna 200 OK")
    void testGetProductImages() throws Exception {
        List<ProductImageResponse> list = List.of(mockResponse(imageId, 0, true));
        when(productImageService.getProductImages(productId)).thenReturn(list);

        mockMvc.perform(get("/api/admin/productos/{id}/imagenes", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(imageId.toString()))
                .andExpect(jsonPath("$[0].is_primary").value(true));
    }

    @Test
    @DisplayName("BE-20: PATCH /api/admin/productos/{id}/imagenes/{imageId}/principal establece la foto de portada y retorna 200 OK")
    void testSetPrimaryImagePatch() throws Exception {
        ProductImageResponse res = mockResponse(imageId, 0, true);
        when(productImageService.setPrimaryImage(productId, imageId)).thenReturn(res);

        mockMvc.perform(patch("/api/admin/productos/{id}/imagenes/{imageId}/principal", productId, imageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(imageId.toString()))
                .andExpect(jsonPath("$.is_primary").value(true))
                .andExpect(jsonPath("$.sort_order").value(0));
    }

    @Test
    @DisplayName("BE-20: PUT /api/admin/productos/{id}/imagenes/orden reordena las fotos y retorna 200 OK")
    void testReorderProductImages() throws Exception {
        ProductImageReorderRequest req = new ProductImageReorderRequest(List.of(
                new ProductImageReorderItemRequest(imageId, 1)
        ));
        List<ProductImageResponse> res = List.of(mockResponse(imageId, 1, false));

        when(productImageService.reorderProductImages(eq(productId), any())).thenReturn(res);

        mockMvc.perform(put("/api/admin/productos/{id}/imagenes/orden", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sort_order").value(1));
    }

    @Test
    @DisplayName("BE-20: DELETE /api/admin/productos/{id}/imagenes/{imageId} elimina la foto y retorna 204 No Content")
    void testDeleteProductImage() throws Exception {
        doNothing().when(productImageService).deleteProductImage(productId, imageId);

        mockMvc.perform(delete("/api/admin/productos/{id}/imagenes/{imageId}", productId, imageId))
                .andExpect(status().isNoContent());

        verify(productImageService).deleteProductImage(productId, imageId);
    }
}
