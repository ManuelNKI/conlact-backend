package com.conlact.conlact_backend.dto.training;

import com.conlact.conlact_backend.entity.TrainingSection;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingSectionResponse(
        UUID id,
        @JsonProperty("titulo") String title,
        @JsonProperty("descripcion") String description,
        @JsonProperty("orden") Integer sortOrder,
        @JsonProperty("publicado") Boolean isPublished,
        @JsonProperty("creado_en") OffsetDateTime createdAt,
        @JsonProperty("actualizado_en") OffsetDateTime updatedAt,
        @JsonProperty("contenidos") List<TrainingContentResponse> contents
) {
    public static TrainingSectionResponse fromEntity(TrainingSection entity, boolean publishedOnly) {
        List<TrainingContentResponse> contentResponses = entity.getContents() == null ? Collections.emptyList() :
                entity.getContents().stream()
                        .filter(c -> !publishedOnly || Boolean.TRUE.equals(c.getIsPublished()))
                        .map(TrainingContentResponse::fromEntity)
                        .toList();

        return TrainingSectionResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .isPublished(entity.getIsPublished())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .contents(contentResponses)
                .build();
    }
}
