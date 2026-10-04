package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.service.TouristAttractionService;
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
class TouristAttractionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TouristAttractionService touristAttractionService;

    @InjectMocks
    private TouristAttractionController touristAttractionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(touristAttractionController).build();
    }

    @Test
    @DisplayName("BE-23: GET /api/turismo y alias /api/tourism retornan 200 y listado de atractivos")
    void shouldReturnAttractionsList() throws Exception {
        UUID id = UUID.randomUUID();
        TouristAttractionResponse attraction = TouristAttractionResponse.builder()
                .id(id)
                .name("Reserva Chimborazo")
                .description("Páramos andinos")
                .nearbyAssociation("Asociación Mulanleo")
                .associationId(UUID.randomUUID())
                .attractionType("Naturaleza")
                .latitude(new BigDecimal("-1.469300"))
                .longitude(new BigDecimal("-78.816900"))
                .requiresConfirmation(true)
                .build();

        when(touristAttractionService.getPublishedAttractions(any())).thenReturn(List.of(attraction));

        mockMvc.perform(get("/api/turismo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Reserva Chimborazo"))
                .andExpect(jsonPath("$[0].asociacion_cercana").value("Asociación Mulanleo"))
                .andExpect(jsonPath("$[0].tipo").value("Naturaleza"))
                .andExpect(jsonPath("$[0].requiere_confirmacion").value(true))
                .andExpect(jsonPath("$[0].lat").value(-1.469300))
                .andExpect(jsonPath("$[0].lng").value(-78.816900));

        mockMvc.perform(get("/api/tourism")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Reserva Chimborazo"));
    }

    @Test
    @DisplayName("BE-23: GET /api/turismo/{id} retorna detalle del atractivo turístico")
    void shouldReturnAttractionById() throws Exception {
        UUID id = UUID.randomUUID();
        TouristAttractionResponse attraction = TouristAttractionResponse.builder()
                .id(id)
                .name("Ruta del Queso")
                .description("Hilado tradicional")
                .build();

        when(touristAttractionService.getPublishedAttractionById(id)).thenReturn(attraction);

        mockMvc.perform(get("/api/turismo/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ruta del Queso"));
    }
}
