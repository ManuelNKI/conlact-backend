package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.image.*;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.AssociationImage;
import com.conlact.conlact_backend.entity.enums.AssociationImageType;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationImageRepository;
import com.conlact.conlact_backend.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssociationImageServiceTest {

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private AssociationImageRepository associationImageRepository;

    @InjectMocks
    private AssociationImageService associationImageService;

    private UUID associationId;
    private Association publishedAssociation;
    private Association unpublishedAssociation;
    private AssociationImage imageFacility;

    @BeforeEach
    void setUp() {
        associationId = UUID.randomUUID();

        publishedAssociation = Association.builder()
                .id(associationId)
                .slug("asociacion-el-lindero")
                .name("Asociación El Lindero")
                .isPublished(true)
                .build();

        unpublishedAssociation = Association.builder()
                .id(associationId)
                .slug("asociacion-borrador")
                .name("Asociación Borrador")
                .isPublished(false)
                .build();

        imageFacility = AssociationImage.builder()
                .id(UUID.randomUUID())
                .association(publishedAssociation)
                .imageType(AssociationImageType.facility)
                .url("https://example.com/instalaciones.jpg")
                .altText("Planta de procesamiento")
                .sortOrder(1)
                .build();
    }

    @Test
    @DisplayName("TC-BE14-13: Consulta galería ordenada con asociación publicada retorna 200 con imágenes")
    void testGetPublishedAssociationImages() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));
        when(associationImageRepository.findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(associationId))
                .thenReturn(List.of(imageFacility));

        AssociationGalleryResponse response = associationImageService.getPublishedAssociationImages(associationId);

        assertThat(response.getAssociationId()).isEqualTo(associationId);
        assertThat(response.getImages()).hasSize(1);
        assertThat(response.getImages().get(0).getImageType()).isEqualTo(AssociationImageType.facility);
        assertThat(response.getImages().get(0).getUrl()).isEqualTo("https://example.com/instalaciones.jpg");
    }

    @Test
    @DisplayName("Consulta galería con asociación no publicada lanza ResourceNotFoundException")
    void testGetPublishedImagesUnpublishedThrowsNotFound() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(unpublishedAssociation));

        assertThatThrownBy(() -> associationImageService.getPublishedAssociationImages(associationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no encontrada o no publicada");
    }

    @Test
    @DisplayName("TC-BE14-01 - TC-BE14-04: Registrar foto válida crea y retorna el recurso")
    void testCreateAssociationImageSuccess() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://storage.conlact.com/fotos/planta.webp")
                .imageType(AssociationImageType.facility)
                .altText("Instalaciones")
                .sortOrder(1)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));
        when(associationImageRepository.existsByAssociationIdAndUrl(associationId, "https://storage.conlact.com/fotos/planta.webp"))
                .thenReturn(false);
        when(associationImageRepository.save(any(AssociationImage.class))).thenAnswer(inv -> {
            AssociationImage entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AssociationImageItemResponse result = associationImageService.createAssociationImage(associationId, request);

        assertThat(result).isNotNull();
        assertThat(result.getUrl()).isEqualTo("https://storage.conlact.com/fotos/planta.webp");
        assertThat(result.getImageType()).isEqualTo(AssociationImageType.facility);
        assertThat(result.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-BE14-05 - TC-BE14-08: Registrar URL no HTTPS o extensión no permitida lanza BadRequestException")
    void testCreateAssociationImageInvalidUrl() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("http://example.com/foto.pdf")
                .imageType(AssociationImageType.seal)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));

        assertThatThrownBy(() -> associationImageService.createAssociationImage(associationId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("TC-BE14-10: sort_order negativo lanza BadRequestException")
    void testCreateAssociationImageNegativeSortOrder() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.producer)
                .sortOrder(-1)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));

        assertThatThrownBy(() -> associationImageService.createAssociationImage(associationId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no puede ser negativo");
    }

    @Test
    @DisplayName("TC-BE14-11: Asociación inexistente al registrar foto lanza ResourceNotFoundException")
    void testCreateAssociationImageAssociationNotFound() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.producer)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> associationImageService.createAssociationImage(associationId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Asociación no encontrada");
    }

    @Test
    @DisplayName("TC-BE14-12: URL duplicada en la misma asociación lanza ConflictException (409)")
    void testCreateAssociationImageDuplicateUrlThrowsConflict() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/instalaciones.jpg")
                .imageType(AssociationImageType.facility)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));
        when(associationImageRepository.existsByAssociationIdAndUrl(associationId, "https://example.com/instalaciones.jpg"))
                .thenReturn(true);

        assertThatThrownBy(() -> associationImageService.createAssociationImage(associationId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya se encuentra vinculada");
    }

    @Test
    @DisplayName("TC-BE14-14 & TC-BE14-15: Actualizar URL y orden modifica los datos exitosamente")
    void testUpdateAssociationImageSuccess() {
        UUID imageId = imageFacility.getId();
        UpdateAssociationImageRequest request = UpdateAssociationImageRequest.builder()
                .url("https://example.com/nueva-planta.png")
                .sortOrder(5)
                .build();

        when(associationRepository.existsById(associationId)).thenReturn(true);
        when(associationImageRepository.findByIdAndAssociationId(imageId, associationId)).thenReturn(Optional.of(imageFacility));
        when(associationImageRepository.existsByAssociationIdAndUrlAndIdNot(associationId, "https://example.com/nueva-planta.png", imageId))
                .thenReturn(false);
        when(associationImageRepository.save(any(AssociationImage.class))).thenAnswer(inv -> inv.getArgument(0));

        AssociationImageItemResponse updated = associationImageService.updateAssociationImage(associationId, imageId, request);

        assertThat(updated.getUrl()).isEqualTo("https://example.com/nueva-planta.png");
        assertThat(updated.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-BE14-17: Desvincular fotografía existente ejecuta delete")
    void testDeleteAssociationImageSuccess() {
        UUID imageId = imageFacility.getId();

        when(associationRepository.existsById(associationId)).thenReturn(true);
        when(associationImageRepository.findByIdAndAssociationId(imageId, associationId)).thenReturn(Optional.of(imageFacility));

        associationImageService.deleteAssociationImage(associationId, imageId);

        verify(associationImageRepository, times(1)).delete(imageFacility);
    }

    @Test
    @DisplayName("TC-BE14-18: Intentar modificar foto de otra asociación lanza ResourceNotFoundException")
    void testUpdateAssociationImageNotFoundForAssociation() {
        UUID imageId = UUID.randomUUID();
        UpdateAssociationImageRequest request = UpdateAssociationImageRequest.builder()
                .sortOrder(2)
                .build();

        when(associationRepository.existsById(associationId)).thenReturn(true);
        when(associationImageRepository.findByIdAndAssociationId(imageId, associationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> associationImageService.updateAssociationImage(associationId, imageId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Fotografía no encontrada");
    }

    @Test
    @DisplayName("TC-BE14-16: Reordenar múltiples fotos actualiza todos los elementos")
    void testReorderAssociationImagesSuccess() {
        UUID imgId1 = UUID.randomUUID();
        UUID imgId2 = UUID.randomUUID();

        AssociationImage img1 = AssociationImage.builder()
                .id(imgId1)
                .association(publishedAssociation)
                .sortOrder(10)
                .build();

        AssociationImage img2 = AssociationImage.builder()
                .id(imgId2)
                .association(publishedAssociation)
                .sortOrder(20)
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));
        when(associationImageRepository.findByIdAndAssociationId(imgId1, associationId)).thenReturn(Optional.of(img1));
        when(associationImageRepository.findByIdAndAssociationId(imgId2, associationId)).thenReturn(Optional.of(img2));
        when(associationImageRepository.findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(associationId))
                .thenReturn(List.of(img1, img2));

        ReorderAssociationImagesRequest reorderReq = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(imgId1, 1),
                        new ImageOrderItemRequest(imgId2, 2)
                ))
                .build();

        AssociationGalleryResponse response = associationImageService.reorderAssociationImages(associationId, reorderReq);

        assertThat(response.getImages()).hasSize(2);
        assertThat(img1.getSortOrder()).isEqualTo(1);
        assertThat(img2.getSortOrder()).isEqualTo(2);
        verify(associationImageRepository, times(2)).save(any(AssociationImage.class));
    }

    @Test
    @DisplayName("Reordenar con IDs duplicados lanza BadRequestException")
    void testReorderAssociationImagesDuplicateIdsThrowsBadRequest() {
        UUID imgId1 = UUID.randomUUID();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));

        ReorderAssociationImagesRequest reorderReq = ReorderAssociationImagesRequest.builder()
                .images(List.of(
                        new ImageOrderItemRequest(imgId1, 1),
                        new ImageOrderItemRequest(imgId1, 2)
                ))
                .build();

        assertThatThrownBy(() -> associationImageService.reorderAssociationImages(associationId, reorderReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duplicados");
    }

    @Test
    @DisplayName("Registrar imagen con alt_text vacío lanza BadRequestException")
    void testCreateImageEmptyAltTextThrowsBadRequest() {
        CreateAssociationImageRequest request = CreateAssociationImageRequest.builder()
                .url("https://example.com/foto.jpg")
                .imageType(AssociationImageType.facility)
                .altText("   ")
                .build();

        when(associationRepository.findById(associationId)).thenReturn(Optional.of(publishedAssociation));

        assertThatThrownBy(() -> associationImageService.createAssociationImage(associationId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cadena vacía");
    }
}
