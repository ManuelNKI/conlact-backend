package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.testimonial.TestimonialResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.service.TestimonialService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TestimonialControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TestimonialService testimonialService;

    @InjectMocks
    private TestimonialController testimonialController;

    private TestimonialResponse sampleTestimonial;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(testimonialController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleTestimonial = new TestimonialResponse(
                UUID.randomUUID(),
                "Mama Rosa Chiguano",
                "Comunera",
                "El queso tierno de Pilahuín sostiene la educación de nuestras familias.",
                java.time.OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("GET /api/testimonios debe retornar lista de testimonios aprobados con HTTP 200")
    void shouldReturnPublicTestimonialsSpanishRoute() throws Exception {
        when(testimonialService.getPublicTestimonials()).thenReturn(List.of(sampleTestimonial));

        mockMvc.perform(get("/api/testimonios")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].autor_nombre").value("Mama Rosa Chiguano"))
                .andExpect(jsonPath("$[0].autor_tipo").value("Comunera"))
                .andExpect(jsonPath("$[0].frase").value("El queso tierno de Pilahuín sostiene la educación de nuestras familias."))
                .andExpect(jsonPath("$[0].fecha").exists());
    }

    @Test
    @DisplayName("GET /api/testimonials (alias en inglés) debe responder idéntico a la ruta en español")
    void shouldReturnPublicTestimonialsEnglishRoute() throws Exception {
        when(testimonialService.getPublicTestimonials()).thenReturn(List.of(sampleTestimonial));

        mockMvc.perform(get("/api/testimonials")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].autor_nombre").value("Mama Rosa Chiguano"));
    }

    @Test
    @DisplayName("GET /api/testimonios debe retornar lista vacía con HTTP 200 cuando no hay testimonios aprobados")
    void shouldReturnEmptyListWhenNoTestimonials() throws Exception {
        when(testimonialService.getPublicTestimonials()).thenReturn(List.of());

        mockMvc.perform(get("/api/testimonios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
