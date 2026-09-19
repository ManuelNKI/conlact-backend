package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssociationServiceTest {

    @Mock
    private AssociationRepository associationRepository;

    @InjectMocks
    private AssociationService associationService;

    private Association testAssociation;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testAssociation = Association.builder()
                .id(testId)
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .shortDescription("Productores artesanales")
                .history("Historia de la asociación...")
                .locationText("Sector El Lindero")
                .latitude(new BigDecimal("-1.298500"))
                .longitude(new BigDecimal("-78.712300"))
                .arcsaRegistration("ARCSA-2023-001")
                .agrocalidadRegistration("AGRO-001")
                .sanitarySealText("Sello Sanitario ARCSA")
                .videoUrl("https://youtube.com/watch?v=123")
                .instagramUrl("https://instagram.com/lindero")
                .tiktokUrl("https://tiktok.com/@lindero")
                .facebookUrl("https://facebook.com/lindero")
                .whatsapp("+593987654321")
                .isPublished(true)
                .build();
    }

    @Test
    @DisplayName("getPublishedAssociations retorna lista mapeada correctamente")
    void testGetPublishedAssociations() {
        when(associationRepository.findByIsPublishedTrue()).thenReturn(List.of(testAssociation));

        List<AssociationResponse> result = associationService.getPublishedAssociations();

        assertThat(result).hasSize(1);
        AssociationResponse response = result.get(0);
        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getSlug()).isEqualTo("asociacion-el-lindero");
        assertThat(response.getName()).isEqualTo("Asociación El Lindero");
        assertThat(response.getShortDescription()).isEqualTo("Productores artesanales");
        assertThat(response.getLocationText()).isEqualTo("Sector El Lindero");
        assertThat(response.getWhatsapp()).isEqualTo("+593987654321");
        assertThat(response.getInstagramUrl()).isEqualTo("https://instagram.com/lindero");
        assertThat(response.getSanitarySeal()).isEqualTo("Sello Sanitario ARCSA");
        verify(associationRepository, times(1)).findByIsPublishedTrue();
    }

    @Test
    @DisplayName("getPublishedAssociationById retorna asociación publicada")
    void testGetPublishedAssociationById_Success() {
        when(associationRepository.findById(testId)).thenReturn(Optional.of(testAssociation));

        AssociationResponse response = associationService.getPublishedAssociationById(testId);

        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getSlug()).isEqualTo("asociacion-el-lindero");
    }

    @Test
    @DisplayName("getPublishedAssociationById lanza 404 si no está publicada")
    void testGetPublishedAssociationById_NotPublished() {
        testAssociation.setIsPublished(false);
        when(associationRepository.findById(testId)).thenReturn(Optional.of(testAssociation));

        assertThatThrownBy(() -> associationService.getPublishedAssociationById(testId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Asociación no encontrada");
    }

    @Test
    @DisplayName("getPublishedAssociationBySlug retorna asociación por slug")
    void testGetPublishedAssociationBySlug_Success() {
        when(associationRepository.findBySlug("asociacion-el-lindero")).thenReturn(Optional.of(testAssociation));

        AssociationResponse response = associationService.getPublishedAssociationBySlug("asociacion-el-lindero");

        assertThat(response.getName()).isEqualTo("Asociación El Lindero");
        assertThat(response.getSlug()).isEqualTo("asociacion-el-lindero");
    }

    @Test
    @DisplayName("getPublishedAssociationByIdOrSlug resuelve UUID o slug dinámicamente")
    void testGetPublishedAssociationByIdOrSlug() {
        when(associationRepository.findById(testId)).thenReturn(Optional.of(testAssociation));
        when(associationRepository.findBySlug("asociacion-el-lindero")).thenReturn(Optional.of(testAssociation));

        AssociationResponse byUuid = associationService.getPublishedAssociationByIdOrSlug(testId.toString());
        AssociationResponse bySlug = associationService.getPublishedAssociationByIdOrSlug("asociacion-el-lindero");

        assertThat(byUuid.getId()).isEqualTo(testId);
        assertThat(bySlug.getSlug()).isEqualTo("asociacion-el-lindero");
    }
}
