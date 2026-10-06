package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.config.CorsConfig;
import com.conlact.conlact_backend.config.SecurityConfig;
import com.conlact.conlact_backend.repository.ProfileRepository;
import com.conlact.conlact_backend.security.JwtAuthenticationFilter;
import com.conlact.conlact_backend.security.JwtAuthenticationEntryPoint;
import com.conlact.conlact_backend.security.JwtTokenProvider;
import com.conlact.conlact_backend.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TestimonialController.class, AdminTestimonialController.class, AdminProductController.class, AdminProductVariantController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AdminEndpointSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private ProfileRepository profileRepository;
    @MockitoBean private TestimonialService testimonialService;
    @MockitoBean private AdminProductService adminProductService;
    @MockitoBean private ProductVariantService productVariantService;
    @MockitoBean private AdminInventoryService adminInventoryService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/productos", "/api/admin/products", "/api/admin/testimonios", "/api/admin/testimonials", "/api/admin/variantes/00000000-0000-0000-0000-000000000001/stock/deduct", "/api/admin/variants/00000000-0000-0000-0000-000000000001/stock/set"})
    @DisplayName("BE-15/BE-19: Toda escritura administrativa requiere autenticación")
    void shouldRejectAnonymousWrites(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/productos", "/api/admin/products", "/api/admin/testimonios", "/api/admin/testimonials", "/api/admin/variantes/00000000-0000-0000-0000-000000000001/stock/deduct", "/api/admin/variants/00000000-0000-0000-0000-000000000001/stock/replenish"})
    @WithMockUser(roles = "USER")
    @DisplayName("BE-15/BE-19: Un usuario autenticado sin rol ADMIN recibe 403")
    void shouldRejectNonAdminWrites(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/testimonios", "/api/testimonials"})
    @DisplayName("BE-15: Consulta pública sin token devuelve una lista vacía válida")
    void shouldAllowAnonymousPublicReads(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BE-19: El rol ADMIN puede consultar el catálogo administrativo")
    void shouldAllowAdminReads() throws Exception {
        mvc.perform(get("/api/admin/productos")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BE-19: El preflight CORS permite las operaciones del frontend configurado")
    void shouldAllowCorsPreflight() throws Exception {
        mvc.perform(options("/api/admin/productos").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprobar", "approve", "archivar", "archive", "moderar", "moderate"})
    @WithMockUser(roles = "USER")
    @DisplayName("Testimonios: Las nuevas acciones de moderación requieren rol ADMIN")
    void shouldProtectModerationActions(String action) throws Exception {
        mvc.perform(patch("/api/admin/testimonios/00000000-0000-0000-0000-000000000001/" + action)
                        .contentType("application/json").content("{\"is_featured\":true}"))
                .andExpect(status().isForbidden());
    }
}
