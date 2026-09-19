package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.repository.AssociationRepository;
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

    private AssociationResponse toResponse(Association association) {
        return AssociationResponse.builder()
                .id(association.getId())
                .slug(association.getSlug())
                .name(association.getName())
                .shortDescription(association.getShortDescription())
                .history(association.getHistory())
                .locationText(association.getLocationText())
                .photos(Collections.emptyList())
                .videoUrl(association.getVideoUrl())
                .sanitarySeal(association.getSanitarySealText())
                .arcsaRegistration(association.getArcsaRegistration())
                .agrocalidadRegistration(association.getAgrocalidadRegistration())
                .latitude(association.getLatitude())
                .longitude(association.getLongitude())
                .whatsapp(association.getWhatsapp())
                .instagramUrl(association.getInstagramUrl())
                .tiktokUrl(association.getTiktokUrl())
                .facebookUrl(association.getFacebookUrl())
                .build();
    }
}
