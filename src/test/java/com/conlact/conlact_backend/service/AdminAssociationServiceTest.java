package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.AssociationCreateRequest;
import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.dto.association.AssociationUpdateRequest;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAssociationServiceTest {

    @Mock
    private AssociationRepository associationRepository;

    @InjectMocks
    private AssociationService associationService;

    private UUID testId;
    private Association existingAssociation;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        existingAssociation = Association.builder()
                .id(testId)
                .name("Asociación El Lindero")
                .slug("asociacion-el-lindero")
                .shortDescription("Quesos de altura")
                .history("Historia tradicional")
                .locationText("Sector El Lindero")
                .latitude(new BigDecimal("-1.298500"))
                .longitude(new BigDecimal("-78.712300"))
                .sanitarySealText("Sello ARCSA")
                .isPublished(true)
                .build();
    }

    @Test
    @DisplayName("getAllAssociations retorna todas las filiales registradas")
    void testGetAllAssociations() {
        when(associationRepository.findAll()).thenReturn(List.of(existingAssociation));

        List<AssociationResponse> result = associationService.getAllAssociations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Asociación El Lindero");
        verify(associationRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAssociationById retorna filial existente")
    void testGetAssociationById_Success() {
        when(associationRepository.findById(testId)).thenReturn(Optional.of(existingAssociation));

        AssociationResponse result = associationService.getAssociationById(testId);

        assertThat(result.getId()).isEqualTo(testId);
        assertThat(result.getName()).isEqualTo("Asociación El Lindero");
    }

    @Test
    @DisplayName("getAssociationById lanza ResourceNotFoundException si no existe")
    void testGetAssociationById_NotFound() {
        UUID nonExisting = UUID.randomUUID();
        when(associationRepository.findById(nonExisting)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> associationService.getAssociationById(nonExisting))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Filial no encontrada");
    }

    @Test
    @DisplayName("createAssociation crea filial y genera slug automáticamente")
    void testCreateAssociation_Success() {
        AssociationCreateRequest request = AssociationCreateRequest.builder()
                .name("Asociación Nueva Esperanza")
                .shortDescription("Productores lácteos")
                .isPublished(true)
                .build();

        when(associationRepository.existsByNameIgnoreCase("Asociación Nueva Esperanza")).thenReturn(false);
        when(associationRepository.existsBySlug("asociacion-nueva-esperanza")).thenReturn(false);
        when(associationRepository.save(any(Association.class))).thenAnswer(invocation -> {
            Association a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AssociationResponse result = associationService.createAssociation(request);

        assertThat(result.getName()).isEqualTo("Asociación Nueva Esperanza");
        assertThat(result.getSlug()).isEqualTo("asociacion-nueva-esperanza");
        assertThat(result.getIsPublished()).isTrue();
    }

    @Test
    @DisplayName("createAssociation lanza ConflictException si el nombre ya está registrado")
    void testCreateAssociation_DuplicateName() {
        AssociationCreateRequest request = AssociationCreateRequest.builder()
                .name("Asociación El Lindero")
                .build();

        when(associationRepository.existsByNameIgnoreCase("Asociación El Lindero")).thenReturn(true);

        assertThatThrownBy(() -> associationService.createAssociation(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe una filial registrada");
    }

    @Test
    @DisplayName("updateAssociation actualiza información institucional")
    void testUpdateAssociation_Success() {
        AssociationUpdateRequest request = AssociationUpdateRequest.builder()
                .name("Asociación El Lindero Modificada")
                .shortDescription("Nueva descripción")
                .isPublished(true)
                .build();

        when(associationRepository.findById(testId)).thenReturn(Optional.of(existingAssociation));
        when(associationRepository.existsByNameIgnoreCaseAndIdNot("Asociación El Lindero Modificada", testId)).thenReturn(false);
        when(associationRepository.save(any(Association.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssociationResponse result = associationService.updateAssociation(testId, request);

        assertThat(result.getName()).isEqualTo("Asociación El Lindero Modificada");
        assertThat(result.getShortDescription()).isEqualTo("Nueva descripción");
    }

    @Test
    @DisplayName("softDeleteAssociation cambia isPublished a false")
    void testSoftDeleteAssociation_Success() {
        when(associationRepository.findById(testId)).thenReturn(Optional.of(existingAssociation));

        associationService.softDeleteAssociation(testId);

        ArgumentCaptor<Association> captor = ArgumentCaptor.forClass(Association.class);
        verify(associationRepository).save(captor.capture());
        assertThat(captor.getValue().getIsPublished()).isFalse();
    }

    @Test
    @DisplayName("restoreAssociation reactiva filial cambiando isPublished a true")
    void testRestoreAssociation_Success() {
        existingAssociation.setIsPublished(false);
        when(associationRepository.findById(testId)).thenReturn(Optional.of(existingAssociation));
        when(associationRepository.save(any(Association.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssociationResponse result = associationService.restoreAssociation(testId);

        assertThat(result.getIsPublished()).isTrue();
    }
}
