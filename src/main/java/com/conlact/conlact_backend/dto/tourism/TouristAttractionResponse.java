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
        @JsonProperty("lat") BigDecimal latitude,
        @JsonProperty("lng") BigDecimal longitude,
        @JsonProperty("requiere_confirmacion") Boolean requiresConfirmation,
        @JsonProperty("condiciones_acceso") String accessConditions,
        @JsonProperty("foto_url") String photoUrl
) {
    public static TouristAttractionResponse fromEntity(TouristAttraction attraction) {
        return TouristAttractionResponse.builder()
                .id(attraction.getId())
                .name(attraction.getName())
                .description(attraction.getDescription())
                .nearbyAssociation(attraction.getAssociation() != null ? attraction.getAssociation().getName() : null)
                .associationId(attraction.getAssociation() != null ? attraction.getAssociation().getId() : null)
                .attractionType(attraction.getAttractionType())
                .latitude(attraction.getLatitude())
                .longitude(attraction.getLongitude())
                .requiresConfirmation(attraction.getRequiresConfirmation())
                .accessConditions(attraction.getAccessConditions())
                .photoUrl(null)
                .build();
    }
}
