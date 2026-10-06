package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.image.*;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.AssociationImage;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationImageRepository;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.validation.AssociationImageUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssociationImageService {

    private final AssociationRepository associationRepository;
    private final AssociationImageRepository associationImageRepository;

    public AssociationGalleryResponse getPublishedAssociationImages(UUID associationId) {
        Association association = associationRepository.findById(associationId)
                .filter(Association::getIsPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada o no publicada"));

        List<AssociationImageItemResponse> images = associationImageRepository
                .findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(association.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        return AssociationGalleryResponse.builder()
                .associationId(association.getId())
                .images(images)
                .build();
    }

    public AssociationGalleryResponse getAllAssociationImagesForAdmin(UUID associationId) {
        Association association = associationRepository.findById(associationId)
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada"));

        List<AssociationImageItemResponse> images = associationImageRepository
                .findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(association.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        return AssociationGalleryResponse.builder()
                .associationId(association.getId())
                .images(images)
                .build();
    }

    @Transactional
    public AssociationImageItemResponse createAssociationImage(UUID associationId, CreateAssociationImageRequest request) {
        Association association = associationRepository.findById(associationId)
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada con id: " + associationId));

        if (!AssociationImageUrlValidator.isValid(request.getUrl())) {
            throw new BadRequestException("La URL debe ser HTTPS válida y tener una extensión permitida (.jpg, .jpeg, .png, .webp)");
        }

        if (request.getImageType() == null) {
            throw new BadRequestException("El tipo de imagen es obligatorio");
        }

        if (request.getSortOrder() != null && request.getSortOrder() < 0) {
            throw new BadRequestException("El orden de despliegue no puede ser negativo");
        }

        String altText = null;
        if (request.getAltText() != null) {
            String trimmed = request.getAltText().trim();
            if (trimmed.isEmpty()) {
                throw new BadRequestException("El texto alternativo no puede ser una cadena vacía");
            }
            if (trimmed.length() > 255) {
                throw new BadRequestException("El texto alternativo no puede superar los 255 caracteres");
            }
            altText = trimmed;
        }

        String normalizedUrl = request.getUrl().trim();
        if (associationImageRepository.existsByAssociationIdAndUrl(associationId, normalizedUrl)) {
            throw new ConflictException("La URL ya se encuentra vinculada a esta asociación");
        }

        AssociationImage image = AssociationImage.builder()
                .association(association)
                .imageType(request.getImageType())
                .url(normalizedUrl)
                .altText(altText)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        AssociationImage saved = associationImageRepository.save(image);
        return toItemResponse(saved);
    }

    @Transactional
    public AssociationImageItemResponse updateAssociationImage(
            UUID associationId,
            UUID imageId,
            UpdateAssociationImageRequest request
    ) {
        if (!associationRepository.existsById(associationId)) {
            throw new ResourceNotFoundException("Asociación no encontrada con id: " + associationId);
        }

        AssociationImage image = associationImageRepository.findByIdAndAssociationId(imageId, associationId)
                .orElseThrow(() -> new ResourceNotFoundException("Fotografía no encontrada para la asociación indicada"));

        if (request.getUrl() != null) {
            String newUrl = request.getUrl().trim();
            if (!AssociationImageUrlValidator.isValid(newUrl)) {
                throw new BadRequestException("La URL debe ser HTTPS válida y tener una extensión permitida (.jpg, .jpeg, .png, .webp)");
            }
            if (associationImageRepository.existsByAssociationIdAndUrlAndIdNot(associationId, newUrl, imageId)) {
                throw new ConflictException("La URL ya se encuentra vinculada a esta asociación");
            }
            image.setUrl(newUrl);
        }

        if (request.getImageType() != null) {
            image.setImageType(request.getImageType());
        }

        if (request.getAltText() != null) {
            String trimmed = request.getAltText().trim();
            if (trimmed.isEmpty()) {
                throw new BadRequestException("El texto alternativo no puede ser una cadena vacía");
            }
            if (trimmed.length() > 255) {
                throw new BadRequestException("El texto alternativo no puede superar los 255 caracteres");
            }
            image.setAltText(trimmed);
        }

        if (request.getSortOrder() != null) {
            if (request.getSortOrder() < 0) {
                throw new BadRequestException("El orden de despliegue no puede ser negativo");
            }
            image.setSortOrder(request.getSortOrder());
        }

        AssociationImage updated = associationImageRepository.save(image);
        return toItemResponse(updated);
    }

    @Transactional
    public void deleteAssociationImage(UUID associationId, UUID imageId) {
        if (!associationRepository.existsById(associationId)) {
            throw new ResourceNotFoundException("Asociación no encontrada con id: " + associationId);
        }

        AssociationImage image = associationImageRepository.findByIdAndAssociationId(imageId, associationId)
                .orElseThrow(() -> new ResourceNotFoundException("Fotografía no encontrada para la asociación indicada"));

        associationImageRepository.delete(image);
    }

    @Transactional
    public AssociationGalleryResponse reorderAssociationImages(UUID associationId, ReorderAssociationImagesRequest request) {
        Association association = associationRepository.findById(associationId)
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada con id: " + associationId));

        if (request.getImages() == null || request.getImages().isEmpty()) {
            throw new BadRequestException("La lista de imágenes a reordenar no puede estar vacía");
        }

        java.util.Set<UUID> seenIds = new java.util.HashSet<>();
        for (ImageOrderItemRequest item : request.getImages()) {
            if (item.getId() == null) {
                throw new BadRequestException("El id de la fotografía es obligatorio en el reordenamiento");
            }
            if (!seenIds.add(item.getId())) {
                throw new BadRequestException("No se permiten identificadores de imagen duplicados en la solicitud de reordenamiento");
            }
            if (item.getSortOrder() == null || item.getSortOrder() < 0) {
                throw new BadRequestException("El orden no puede ser negativo");
            }
        }

        for (ImageOrderItemRequest item : request.getImages()) {
            AssociationImage image = associationImageRepository.findByIdAndAssociationId(item.getId(), associationId)
                    .orElseThrow(() -> new ResourceNotFoundException("La imagen con id " + item.getId() + " no pertenece a la asociación indicada"));

            image.setSortOrder(item.getSortOrder());
            associationImageRepository.save(image);
        }

        List<AssociationImageItemResponse> updatedImages = associationImageRepository
                .findByAssociationIdOrderBySortOrderAscCreatedAtAscIdAsc(association.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        return AssociationGalleryResponse.builder()
                .associationId(association.getId())
                .images(updatedImages)
                .build();
    }

    private AssociationImageItemResponse toItemResponse(AssociationImage entity) {
        return AssociationImageItemResponse.builder()
                .id(entity.getId())
                .imageType(entity.getImageType())
                .url(entity.getUrl())
                .altText(entity.getAltText())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
