package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.entity.TouristAttraction;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TouristAttractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TouristAttractionService {

    private final TouristAttractionRepository touristAttractionRepository;

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
}
