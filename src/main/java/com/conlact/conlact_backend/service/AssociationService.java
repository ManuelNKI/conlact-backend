package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.AssociationCreateRequest;
import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.dto.association.AssociationUpdateRequest;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssociationService {

    private final AssociationRepository associationRepository;

    // ==========================================
    // Endpoints Públicos (Semana 3 - BE-12)
    // ==========================================

    public List<AssociationResponse> getPublishedAssociations() {
        return associationRepository.findByIsPublishedTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AssociationResponse getPublishedAssociationById(UUID associationId) {
        Association association = associationRepository.findById(associationId)
                .filter(Association::getIsPublished)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Asociación no encontrada"
                        )
                );

        return toResponse(association);
    }

    public AssociationResponse getPublishedAssociationBySlug(String slug) {
        Association association = associationRepository.findBySlug(slug)
                .filter(Association::getIsPublished)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Asociación no encontrada"
                        )
                );

        return toResponse(association);
    }

    public AssociationResponse getPublishedAssociationByIdOrSlug(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return getPublishedAssociationById(id);
        } catch (IllegalArgumentException e) {
            return getPublishedAssociationBySlug(identifier);
        }
    }

    // ==========================================
    // Endpoints Administrativos (Semana 3 - BE-13)
    // ==========================================

    public List<AssociationResponse> getAllAssociations() {
        return associationRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AssociationResponse getAssociationById(UUID id) {
        Association association = associationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filial no encontrada con id: " + id));

        return toResponse(association);
    }

    @Transactional
    public AssociationResponse createAssociation(AssociationCreateRequest request) {
        if (associationRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ConflictException("Ya existe una filial registrada con el nombre: " + request.getName().trim());
        }

        String slug = resolveUniqueSlugForCreation(request.getSlug(), request.getName());

        Association association = Association.builder()
                .name(request.getName().trim())
                .slug(slug)
                .shortDescription(request.getShortDescription())
                .history(request.getHistory())
                .locationText(request.getLocationText())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .arcsaRegistration(request.getArcsaRegistration())
                .agrocalidadRegistration(request.getAgrocalidadRegistration())
                .sanitarySealText(request.getSanitarySealText())
                .videoUrl(request.getVideoUrl())
                .instagramUrl(request.getInstagramUrl())
                .tiktokUrl(request.getTiktokUrl())
                .facebookUrl(request.getFacebookUrl())
                .whatsapp(request.getWhatsapp())
                .isPublished(request.getIsPublished() != null ? request.getIsPublished() : true)
                .build();

        Association saved = associationRepository.save(association);
        return toResponse(saved);
    }

    @Transactional
    public AssociationResponse updateAssociation(UUID id, AssociationUpdateRequest request) {
        Association association = associationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filial no encontrada con id: " + id));

        String trimmedName = request.getName().trim();
        if (associationRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException("Ya existe otra filial registrada con el nombre: " + trimmedName);
        }

        String slug = resolveUniqueSlugForUpdate(request.getSlug(), trimmedName, association);

        association.setName(trimmedName);
        association.setSlug(slug);
        association.setShortDescription(request.getShortDescription());
        association.setHistory(request.getHistory());
        association.setLocationText(request.getLocationText());
        association.setLatitude(request.getLatitude());
        association.setLongitude(request.getLongitude());
        association.setArcsaRegistration(request.getArcsaRegistration());
        association.setAgrocalidadRegistration(request.getAgrocalidadRegistration());
        association.setSanitarySealText(request.getSanitarySealText());
        association.setVideoUrl(request.getVideoUrl());
        association.setInstagramUrl(request.getInstagramUrl());
        association.setTiktokUrl(request.getTiktokUrl());
        association.setFacebookUrl(request.getFacebookUrl());
        association.setWhatsapp(request.getWhatsapp());

        if (request.getIsPublished() != null) {
            association.setIsPublished(request.getIsPublished());
        }

        Association updated = associationRepository.save(association);
        return toResponse(updated);
    }

    @Transactional
    public void softDeleteAssociation(UUID id) {
        Association association = associationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filial no encontrada con id: " + id));

        association.setIsPublished(false);
        associationRepository.save(association);
    }

    @Transactional
    public AssociationResponse restoreAssociation(UUID id) {
        Association association = associationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filial no encontrada con id: " + id));

        association.setIsPublished(true);
        Association restored = associationRepository.save(association);
        return toResponse(restored);
    }

    // ==========================================
    // Métodos Auxiliares
    // ==========================================

    private String resolveUniqueSlugForCreation(String requestedSlug, String name) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String cleanSlug = SlugUtils.toSlug(requestedSlug);
            if (associationRepository.existsBySlug(cleanSlug)) {
                throw new ConflictException("El slug '" + cleanSlug + "' ya se encuentra en uso");
            }
            return cleanSlug;
        }

        String baseSlug = SlugUtils.toSlug(name);
        String candidateSlug = baseSlug;
        int counter = 2;
        while (associationRepository.existsBySlug(candidateSlug)) {
            candidateSlug = baseSlug + "-" + counter++;
        }
        return candidateSlug;
    }

    private String resolveUniqueSlugForUpdate(String requestedSlug, String name, Association existing) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String cleanSlug = SlugUtils.toSlug(requestedSlug);
            if (associationRepository.existsBySlugAndIdNot(cleanSlug, existing.getId())) {
                throw new ConflictException("El slug '" + cleanSlug + "' ya se encuentra en uso");
            }
            return cleanSlug;
        }

        if (existing.getSlug() != null && !existing.getSlug().isBlank()) {
            return existing.getSlug();
        }

        String baseSlug = SlugUtils.toSlug(name);
        String candidateSlug = baseSlug;
        int counter = 2;
        while (associationRepository.existsBySlugAndIdNot(candidateSlug, existing.getId())) {
            candidateSlug = baseSlug + "-" + counter++;
        }
        return candidateSlug;
    }

    private AssociationResponse toResponse(Association association) {
        AssociationResponse.SocialNetworksDto socialNetworks = AssociationResponse.SocialNetworksDto.builder()
                .facebook(association.getFacebookUrl())
                .instagram(association.getInstagramUrl())
                .tiktok(association.getTiktokUrl())
                .whatsapp(association.getWhatsapp())
                .build();

        return AssociationResponse.builder()
                .id(association.getId())
                .slug(association.getSlug())
                .name(association.getName())
                .shortDescription(association.getShortDescription())
                .history(association.getHistory())
                .locationText(association.getLocationText())
                .referenceLocation(association.getLocationText())
                .photos(Collections.emptyList())
                .videoUrl(association.getVideoUrl())
                .sanitarySeal(association.getSanitarySealText())
                .arcsaRegistration(association.getArcsaRegistration())
                .arcsaSeal(association.getArcsaRegistration())
                .agrocalidadRegistration(association.getAgrocalidadRegistration())
                .bpmRegistration(association.getAgrocalidadRegistration())
                .latitude(association.getLatitude())
                .longitude(association.getLongitude())
                .whatsapp(association.getWhatsapp())
                .associationContact(association.getWhatsapp())
                .instagramUrl(association.getInstagramUrl())
                .tiktokUrl(association.getTiktokUrl())
                .facebookUrl(association.getFacebookUrl())
                .socialNetworks(socialNetworks)
                .isPublished(association.getIsPublished())
                .createdAt(association.getCreatedAt())
                .updatedAt(association.getUpdatedAt())
                .build();
    }
}
