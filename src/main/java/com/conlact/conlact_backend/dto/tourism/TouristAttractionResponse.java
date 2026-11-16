package com.conlact.conlact_backend.dto.tourism;

import com.conlact.conlact_backend.entity.TouristAttraction;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TouristAttractionResponse(
        UUID id,
        @JsonProperty("nombre") String name,
        @JsonProperty("descripcion") String description,
        @JsonProperty("asociacion_cercana") String nearbyAssociation,
        @JsonProperty("asociacion_id") UUID associationId,
        @JsonProperty("tipo") String attractionType,
        @JsonProperty("duracion") String duration,
        @JsonProperty("dificultad") String difficulty,
        @JsonProperty("video_url") String videoUrl,
        @JsonProperty("lat") BigDecimal latitude,
        @JsonProperty("lng") BigDecimal longitude,
        @JsonProperty("requiere_confirmacion") Boolean requiresConfirmation,
        @JsonProperty("condiciones_acceso") String accessConditions,
        @JsonProperty("foto_url") String photoUrl,
        @JsonProperty("is_published") Boolean isPublished
) {
    public static TouristAttractionResponse fromEntity(TouristAttraction attraction) {
        return TouristAttractionResponse.builder()
                .id(attraction.getId())
                .name(attraction.getName())
                .description(attraction.getDescription())
                .nearbyAssociation(attraction.getAssociation() != null ? attraction.getAssociation().getName() : null)
                .associationId(attraction.getAssociation() != null ? attraction.getAssociation().getId() : null)
                .attractionType(attraction.getAttractionType())
                .duration(attraction.getDuration())
                .difficulty(attraction.getDifficulty())
                .videoUrl(attraction.getVideoUrl())
                .latitude(attraction.getLatitude())
                .longitude(attraction.getLongitude())
                .requiresConfirmation(attraction.getRequiresConfirmation())
                .accessConditions(attraction.getAccessConditions())
                .photoUrl(attraction.getImageUrl())
                .isPublished(attraction.getIsPublished())
                .build();
    }
}
