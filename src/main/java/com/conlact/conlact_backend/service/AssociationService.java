package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.repository.AssociationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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

    private AssociationResponse toResponse(Association association) {

        return AssociationResponse.builder()
                .id(association.getId())
                .name(association.getName())
                .history(association.getHistory())
                .photos(Collections.emptyList())
                .videoUrl(association.getVideoUrl())
                .sanitarySeal(association.getSanitarySealText())
                .latitude(association.getLatitude())
                .longitude(association.getLongitude())
                .build();
    }
}