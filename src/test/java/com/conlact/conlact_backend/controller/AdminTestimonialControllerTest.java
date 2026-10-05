package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.testimonial.AdminTestimonialResponse;
import com.conlact.conlact_backend.dto.testimonial.TestimonialPublicationRequest;
import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.service.TestimonialService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminTestimonialControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TestimonialService testimonialService;

    @InjectMocks
    private AdminTestimonialController adminTestimonialController;

    private UUID testimonialId;
    private AdminTestimonialResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminTestimonialController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testimonialId = UUID.randomUUID();
        sampleResponse = new AdminTestimonialResponse(
                testimonialId,
                "Mercedes Caisabanda",
                "Maestra Quesera",
                "Elaboramos quesos artesanales con identidad tungurahuense.",
                true,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("GET /api/admin/testimonios debe retornar todos los testimonios con HTTP 200")
    void shouldReturnAllTestimonialsForAdmin() throws Exception {
        when(testimonialService.getAllTestimonials()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/admin/testimonios"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(testimonialId.toString()))
                .andExpect(jsonPath("$[0].autor_nombre").value("Mercedes Caisabanda"))
                .andExpect(jsonPath("$[0].is_authorized").value(true))
                .andExpect(jsonPath("$[0].is_published").value(true));
    }

    @Test
    @DisplayName("GET /api/admin/testimonials (alias) debe retornar los testimonios correctamente")
    void shouldSupportEnglishAliasForAdminList() throws Exception {
        when(testimonialService.getAllTestimonials()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/admin/testimonials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(testimonialId.toString()));
    }

    @Test
    @DisplayName("GET /api/admin/testimonios/{id} debe retornar 200 con el detalle del testimonio")
    void shouldReturnTestimonialById() throws Exception {
        when(testimonialService.getTestimonialById(testimonialId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/admin/testimonios/{id}", testimonialId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testimonialId.toString()))
                .andExpect(jsonPath("$.autor_nombre").value("Mercedes Caisabanda"));
    }

    @Test
    @DisplayName("GET /api/admin/testimonios/{id} no existente debe retornar 404 Not Found")
    void shouldReturn404WhenTestimonialNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(testimonialService.getTestimonialById(missingId))
                .thenThrow(new ResourceNotFoundException("Testimonio no encontrado"));

        mockMvc.perform(get("/api/admin/testimonios/{id}", missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/admin/testimonios con datos válidos debe retornar 201 Created")
    void shouldCreateTestimonialSuccessfully() throws Exception {
        TestimonialRequest request = new TestimonialRequest(
                "Carlos Morales",
                "Comprador Frecuente",
                "Excelente maduración y sabor auténtico de páramo.",
                true,
                false
        );

        AdminTestimonialResponse createdResponse = new AdminTestimonialResponse(
                testimonialId,
                request.authorName(),
                request.authorType(),
                request.quote(),
                true,
                false,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(testimonialService.createTestimonial(any(TestimonialRequest.class))).thenReturn(createdResponse);

        mockMvc.perform(post("/api/admin/testimonios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testimonialId.toString()))
                .andExpect(jsonPath("$.autor_nombre").value("Carlos Morales"))
                .andExpect(jsonPath("$.is_published").value(false));
    }

    @Test
    @DisplayName("POST /api/admin/testimonios con autor o frase en blanco debe retornar 400 Bad Request")
    void shouldRejectBlankFieldsOnCreate() throws Exception {
        String invalidJson = """
                {
                    "autor_nombre": "",
                    "frase": ""
                }
                """;

        mockMvc.perform(post("/api/admin/testimonios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/admin/testimonios/{id}/publicar debe modificar el estado de publicación con 200 OK")
    void shouldPublishTestimonial() throws Exception {
        AdminTestimonialResponse publishedResponse = new AdminTestimonialResponse(
                testimonialId,
                sampleResponse.authorName(),
                sampleResponse.authorType(),
                sampleResponse.quote(),
                true,
                true,
                sampleResponse.date(),
                OffsetDateTime.now()
        );

        when(testimonialService.changePublication(eq(testimonialId), eq(true))).thenReturn(publishedResponse);

        String payload = """
                { "is_published": true }
                """;

        mockMvc.perform(patch("/api/admin/testimonios/{id}/publicar", testimonialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_published").value(true));
    }

    @Test
    @DisplayName("DELETE /api/admin/testimonios/{id} debe eliminar y responder con 204 No Content")
    void shouldDeleteTestimonial() throws Exception {
        doNothing().when(testimonialService).deleteTestimonial(testimonialId);

        mockMvc.perform(delete("/api/admin/testimonios/{id}", testimonialId))
                .andExpect(status().isNoContent());
    }
}
