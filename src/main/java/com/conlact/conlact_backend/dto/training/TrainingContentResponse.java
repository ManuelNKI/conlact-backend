package com.conlact.conlact_backend.dto.training;

import com.conlact.conlact_backend.entity.TrainingContent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingContentResponse(
        UUID id,
        @JsonProperty("seccion_id") UUID sectionId,
        @JsonProperty("titulo") String title,
        @JsonProperty("descripcion") String description,
        @JsonProperty("tipo") String type,
        @JsonProperty("url") String url,
        @JsonProperty("cuerpo") String body,
        @JsonProperty("orden") Integer sortOrder,
        @JsonProperty("publicado") Boolean isPublished,
        @JsonProperty("creado_en") OffsetDateTime createdAt,
        @JsonProperty("actualizado_en") OffsetDateTime updatedAt
) {
    public static TrainingContentResponse fromEntity(TrainingContent entity) {
        return TrainingContentResponse.builder()
                .id(entity.getId())
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .title(entity.getTitle())
                .description(entity.getDescription())
                .type(entity.getContentType())
                .url(entity.getUrl())
                .body(entity.getBody())
                .sortOrder(entity.getSortOrder())
                .isPublished(entity.getIsPublished())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
