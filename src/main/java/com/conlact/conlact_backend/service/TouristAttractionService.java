package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionRequest;
import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.TouristAttraction;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.repository.TouristAttractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TouristAttractionService {

    private final TouristAttractionRepository touristAttractionRepository;
    private final AssociationRepository associationRepository;

    @Autowired
    public TouristAttractionService(TouristAttractionRepository touristAttractionRepository,
                                   AssociationRepository associationRepository) {
        this.touristAttractionRepository = touristAttractionRepository;
        this.associationRepository = associationRepository;
    }

    public TouristAttractionService(TouristAttractionRepository touristAttractionRepository) {
        this(touristAttractionRepository, null);
    }

    // ==========================================
    // Endpoints Públicos
    // ==========================================

    public List<TouristAttractionResponse> getPublishedAttractions(UUID associationId) {
        List<TouristAttraction> attractions = associationId != null
                ? touristAttractionRepository.findByAssociationIdAndIsPublishedTrue(associationId)
                : touristAttractionRepository.findByIsPublishedTrue();

        return attractions.stream()
                .map(TouristAttractionResponse::fromEntity)
                .toList();
    }

    public TouristAttractionResponse getPublishedAttractionById(UUID id) {
        TouristAttraction attraction = touristAttractionRepository.findById(id)
                .filter(TouristAttraction::getIsPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Atractivo turístico no encontrado con ID: " + id));

        return TouristAttractionResponse.fromEntity(attraction);
    }

    // ==========================================
    // Endpoints Administrativos
    // ==========================================

    public List<TouristAttractionResponse> getAllAttractions() {
        return touristAttractionRepository.findAll().stream()
                .map(TouristAttractionResponse::fromEntity)
                .toList();
    }

    public TouristAttractionResponse getAttractionById(UUID id) {
        TouristAttraction attraction = touristAttractionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atractivo turístico no encontrado con ID: " + id));
        return TouristAttractionResponse.fromEntity(attraction);
    }

    @Transactional
    public TouristAttractionResponse createAttraction(TouristAttractionRequest request) {
        Association association = null;
        if (request.associationId() != null) {
            association = associationRepository.findById(request.associationId())
                    .orElse(null);
        }

        TouristAttraction attraction = TouristAttraction.builder()
                .name(request.name().trim())
                .description(request.description())
                .duration(request.duration())
                .difficulty(request.difficulty())
                .imageUrl(request.photoUrl())
                .videoUrl(request.videoUrl())
                .association(association)
                .attractionType(request.attractionType())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .requiresConfirmation(request.requiresConfirmation() != null ? request.requiresConfirmation() : true)
                .accessConditions(request.accessConditions())
                .isPublished(Boolean.TRUE.equals(request.isPublished()))
                .build();

        TouristAttraction saved = touristAttractionRepository.save(attraction);
        return TouristAttractionResponse.fromEntity(saved);
    }

    @Transactional
    public TouristAttractionResponse updateAttraction(UUID id, TouristAttractionRequest request) {
        TouristAttraction attraction = touristAttractionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atractivo turístico no encontrado con ID: " + id));

        if (request.associationId() != null) {
            Association association = associationRepository.findById(request.associationId()).orElse(null);
            attraction.setAssociation(association);
        }

        attraction.setName(request.name().trim());
        attraction.setDescription(request.description());
        attraction.setDuration(request.duration());
        attraction.setDifficulty(request.difficulty());
        if (request.photoUrl() != null) {
            attraction.setImageUrl(request.photoUrl());
        }
        if (request.videoUrl() != null) {
            attraction.setVideoUrl(request.videoUrl());
        }
        if (request.attractionType() != null) {
            attraction.setAttractionType(request.attractionType());
        }
        if (request.latitude() != null) {
            attraction.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            attraction.setLongitude(request.longitude());
        }
        if (request.requiresConfirmation() != null) {
            attraction.setRequiresConfirmation(request.requiresConfirmation());
        }
        if (request.accessConditions() != null) {
            attraction.setAccessConditions(request.accessConditions());
        }
        if (request.isPublished() != null) {
            attraction.setIsPublished(request.isPublished());
        }

        TouristAttraction updated = touristAttractionRepository.save(attraction);
        return TouristAttractionResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteAttraction(UUID id) {
        if (!touristAttractionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Atractivo turístico no encontrado con ID: " + id);
        }
        touristAttractionRepository.deleteById(id);
    }

    @Transactional
    public TouristAttractionResponse changePublication(UUID id, Boolean published) {
        TouristAttraction attraction = touristAttractionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atractivo turístico no encontrado con ID: " + id));

        boolean newStatus = published != null ? published : !Boolean.TRUE.equals(attraction.getIsPublished());
        attraction.setIsPublished(newStatus);
        TouristAttraction updated = touristAttractionRepository.save(attraction);
        return TouristAttractionResponse.fromEntity(updated);
    }
}
