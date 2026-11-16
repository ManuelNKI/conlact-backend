package com.conlact.conlact_backend.dto.cms;

import com.conlact.conlact_backend.entity.PageContent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageContentResponse(
        String slug,
        String eyebrow,
        @JsonProperty("titulo") String title,
        @JsonProperty("subtitulo") String subtitle,
        @JsonProperty("contenido") Map<String, Object> content,
        Long version,
        @JsonProperty("actualizado_en") OffsetDateTime updatedAt
) {
    public static PageContentResponse fromEntity(PageContent entity) {
        return PageContentResponse.builder()
                .slug(entity.getSlug())
                .eyebrow(entity.getEyebrow() != null ? entity.getEyebrow() : "")
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle() != null ? entity.getSubtitle() : "")
                .content(entity.getContentJson())
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
