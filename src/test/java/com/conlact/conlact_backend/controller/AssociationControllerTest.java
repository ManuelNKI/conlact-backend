package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.service.AssociationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AssociationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssociationService associationService;

    @InjectMocks
    private AssociationController associationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(associationController).build();
    }

    @Test
    @DisplayName("GET /api/associations retorna 200 y lista de asociaciones con nuevos campos")
    void testGetAssociationsPublic() throws Exception {
        UUID id = UUID.randomUUID();
        AssociationResponse response = AssociationResponse.builder()
                .id(id)
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .shortDescription("Productores artesanales")
                .history("Historia de la asociación...")
                .locationText("Sector El Lindero")
                .referenceLocation("Sector El Lindero")
                .sanitarySeal("Sello Sanitario ARCSA")
                .arcsaRegistration("ARCSA-2023-001")
                .arcsaSeal("ARCSA-2023-001")
                .agrocalidadRegistration("AGRO-001")
                .bpmRegistration("AGRO-001")
                .latitude(new BigDecimal("-1.298500"))
                .longitude(new BigDecimal("-78.712300"))
                .whatsapp("+593987654321")
                .associationContact("+593987654321")
                .instagramUrl("https://instagram.com/lindero")
                .tiktokUrl("https://tiktok.com/@lindero")
                .facebookUrl("https://facebook.com/lindero")
                .socialNetworks(AssociationResponse.SocialNetworksDto.builder()
                        .whatsapp("+593987654321")
                        .facebook("https://facebook.com/lindero")
                        .instagram("https://instagram.com/lindero")
                        .tiktok("https://tiktok.com/@lindero")
                        .build())
                .photos(Collections.emptyList())
                .build();

        when(associationService.getPublishedAssociations()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/associations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("asociacion-el-lindero"))
                .andExpect(jsonPath("$[0].nombre").value("Asociación El Lindero"))
                .andExpect(jsonPath("$[0].descripcion_corta").value("Productores artesanales"))
                .andExpect(jsonPath("$[0].ubicacion").value("Sector El Lindero"))
                .andExpect(jsonPath("$[0].ubicacion_referencia").value("Sector El Lindero"))
                .andExpect(jsonPath("$[0].whatsapp").value("+593987654321"))
                .andExpect(jsonPath("$[0].contacto_asociacion").value("+593987654321"))
                .andExpect(jsonPath("$[0].instagram_url").value("https://instagram.com/lindero"))
                .andExpect(jsonPath("$[0].registro_arcsa").value("ARCSA-2023-001"))
                .andExpect(jsonPath("$[0].sello_arcsa").value("ARCSA-2023-001"))
                .andExpect(jsonPath("$[0].registro_agrocalidad").value("AGRO-001"))
                .andExpect(jsonPath("$[0].registro_bpm").value("AGRO-001"))
                .andExpect(jsonPath("$[0].redes_sociales.whatsapp").value("+593987654321"))
                .andExpect(jsonPath("$[0].fotos").isArray())
                .andExpect(jsonPath("$[0].lat").value(-1.298500))
                .andExpect(jsonPath("$[0].lng").value(-78.712300));
    }

    @Test
    @DisplayName("GET /api/asociaciones (alias en español para frontend y BE-12) retorna 200")
    void testGetAsociacionesSpanishEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        AssociationResponse response = AssociationResponse.builder()
                .id(id)
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .photos(Collections.emptyList())
                .build();

        when(associationService.getPublishedAssociations()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/asociaciones")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("asociacion-el-lindero"))
                .andExpect(jsonPath("$[0].nombre").value("Asociación El Lindero"));
    }

    @Test
    @DisplayName("GET /api/asociaciones/{identifier} resuelve correctamente por slug")
    void testGetAsociacionBySlugSpanish() throws Exception {
        AssociationResponse response = AssociationResponse.builder()
                .id(UUID.randomUUID())
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .build();

        when(associationService.getPublishedAssociationByIdOrSlug("asociacion-el-lindero")).thenReturn(response);

        mockMvc.perform(get("/api/asociaciones/asociacion-el-lindero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("asociacion-el-lindero"))
                .andExpect(jsonPath("$.nombre").value("Asociación El Lindero"));
    }

    @Test
    @DisplayName("GET /api/associations/{identifier} resuelve correctamente por slug")
    void testGetAssociationBySlug() throws Exception {
        AssociationResponse response = AssociationResponse.builder()
                .id(UUID.randomUUID())
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .build();

        when(associationService.getPublishedAssociationByIdOrSlug("asociacion-el-lindero")).thenReturn(response);

        mockMvc.perform(get("/api/associations/asociacion-el-lindero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("asociacion-el-lindero"))
                .andExpect(jsonPath("$.nombre").value("Asociación El Lindero"));
    }

    @Test
    @DisplayName("GET /api/associations/{identifier} inexistente retorna 404")
    void testGetAssociationNotFound() throws Exception {
        String missingId = UUID.randomUUID().toString();
        when(associationService.getPublishedAssociationByIdOrSlug(missingId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asociación no encontrada"));

        mockMvc.perform(get("/api/associations/" + missingId))
                .andExpect(status().isNotFound());
    }
}
