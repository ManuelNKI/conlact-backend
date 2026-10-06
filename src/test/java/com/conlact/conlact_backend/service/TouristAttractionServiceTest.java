package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.TouristAttraction;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TouristAttractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TouristAttractionServiceTest {

    @Mock
    private TouristAttractionRepository touristAttractionRepository;

    private TouristAttractionService touristAttractionService;

    @BeforeEach
    void setUp() {
        touristAttractionService = new TouristAttractionService(touristAttractionRepository);
    }

    private TouristAttraction createSampleAttraction(String name, boolean published) {
        Association assoc = Association.builder()
                .id(UUID.randomUUID())
                .name("Asociación Mulanleo")
                .slug("mulanleo")
                .build();

        return TouristAttraction.builder()
                .id(UUID.randomUUID())
                .name(name)
                .association(assoc)
                .attractionType("Naturaleza & Senderismo")
                .description("Extensos páramos andinos")
                .accessConditions("Vía de segundo orden")
                .latitude(new BigDecimal("-1.469300"))
                .longitude(new BigDecimal("-78.816900"))
                .requiresConfirmation(true)
                .isPublished(published)
                .build();
    }

    @Test
    @DisplayName("BE-23: Debe listar todos los atractivos turísticos publicados")
    void shouldListPublishedAttractions() {
        TouristAttraction attraction = createSampleAttraction("Reserva de Fauna Chimborazo", true);
        when(touristAttractionRepository.findByIsPublishedTrue()).thenReturn(List.of(attraction));

        List<TouristAttractionResponse> response = touristAttractionService.getPublishedAttractions(null);

        assertThat(response).hasSize(1);
        TouristAttractionResponse dto = response.getFirst();
        assertThat(dto.name()).isEqualTo("Reserva de Fauna Chimborazo");
        assertThat(dto.nearbyAssociation()).isEqualTo("Asociación Mulanleo");
        assertThat(dto.attractionType()).isEqualTo("Naturaleza & Senderismo");
        assertThat(dto.requiresConfirmation()).isTrue();
        assertThat(dto.latitude()).isEqualByComparingTo("-1.469300");
    }

    @Test
    @DisplayName("BE-23: Debe filtrar atractivos por ID de asociación cercana")
    void shouldFilterByAssociation() {
        UUID assocId = UUID.randomUUID();
        TouristAttraction attraction = createSampleAttraction("Ruta del Queso", true);
        when(touristAttractionRepository.findByAssociationIdAndIsPublishedTrue(assocId)).thenReturn(List.of(attraction));

        List<TouristAttractionResponse> response = touristAttractionService.getPublishedAttractions(assocId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().name()).isEqualTo("Ruta del Queso");
    }

    @Test
    @DisplayName("BE-23: Debe obtener detalle de atractivo publicado o lanzar 404")
    void shouldGetAttractionByIdOrThrow() {
        TouristAttraction published = createSampleAttraction("Mirador El Lindero", true);
        UUID id = published.getId();
        when(touristAttractionRepository.findById(id)).thenReturn(Optional.of(published));

        TouristAttractionResponse response = touristAttractionService.getPublishedAttractionById(id);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Mirador El Lindero");

        UUID nonExistent = UUID.randomUUID();
        when(touristAttractionRepository.findById(nonExistent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> touristAttractionService.getPublishedAttractionById(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Atractivo turístico no encontrado");
    }
}
