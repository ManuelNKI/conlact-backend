package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.product.ProductPublicResponse;
import com.conlact.conlact_backend.service.ProductService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @Mock
    private com.conlact.conlact_backend.service.ProductImageService productImageService;

    @InjectMocks
    private ProductController productController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    private ProductPublicResponse createMockProductResponse() {
        return ProductPublicResponse.builder()
                .id(UUID.randomUUID())
                .name("Queso Fresco")
                .slug("queso-fresco-el-lindero")
                .cheeseType("Fresco Semidesnatado")
                .weight("500 g")
                .price(new BigDecimal("3.25"))
                .shortDescription("Queso artesanal")
                .description("Descripción")
                .originText("Pilahuín")
                .conservation("2°C a 6°C")
                .photos(List.of("https://supabase.co/img.jpg"))
                .associationName("Asociación El Lindero")
                .associationId(UUID.randomUUID())
                .categoryName("Fresco")
                .categoryId(UUID.randomUUID())
                .stock(20)
                .available(true)
                .presentations(List.of(
                        ProductPublicResponse.PresentationResponse.builder()
                                .id(UUID.randomUUID())
                                .sku("LIND-QFR-500")
                                .presentationName("Bloque 500 g")
                                .weightGrams(500)
                                .price(new BigDecimal("3.25"))
                                .stock(20)
                                .lowStock(false)
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("BE-17: GET /api/productos y alias /api/products retornan 200 y listado de productos")
    void shouldReturnProductsList() throws Exception {
        ProductPublicResponse product = createMockProductResponse();
        when(productService.getPublishedProducts(any(), any(), any(), any(), any())).thenReturn(List.of(product));

        mockMvc.perform(get("/api/productos")
                        .param("categoria", "fresco")
                        .param("destacado", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Queso Fresco"))
                .andExpect(jsonPath("$[0].slug").value("queso-fresco-el-lindero"))
                .andExpect(jsonPath("$[0].tipo_queso").value("Fresco Semidesnatado"))
                .andExpect(jsonPath("$[0].peso").value("500 g"))
                .andExpect(jsonPath("$[0].precio").value(3.25))
                .andExpect(jsonPath("$[0].disponible").value(true))
                .andExpect(jsonPath("$[0].asociacion").value("Asociación El Lindero"))
                .andExpect(jsonPath("$[0].presentaciones[0].sku").value("LIND-QFR-500"));

        mockMvc.perform(get("/api/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Queso Fresco"));
    }

    @Test
    @DisplayName("BE-17: GET /api/productos/{identifier} retorna detalle del producto")
    void shouldReturnProductDetail() throws Exception {
        ProductPublicResponse product = createMockProductResponse();
        when(productService.getPublishedProductByIdOrSlug("queso-fresco-el-lindero")).thenReturn(product);

        mockMvc.perform(get("/api/productos/queso-fresco-el-lindero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Queso Fresco"))
                .andExpect(jsonPath("$.slug").value("queso-fresco-el-lindero"))
                .andExpect(jsonPath("$.descripcion_corta").value("Queso artesanal"))
                .andExpect(jsonPath("$.origen").value("Pilahuín"))
                .andExpect(jsonPath("$.conservacion").value("2°C a 6°C"));
    }
}
