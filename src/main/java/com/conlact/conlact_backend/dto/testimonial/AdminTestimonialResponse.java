package com.conlact.conlact_backend.dto.testimonial;

import com.conlact.conlact_backend.entity.Testimonial;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTestimonialResponse(
        UUID id,
        @JsonProperty("autor_nombre") String authorName,
        @JsonProperty("autor_tipo") String authorType,
        @JsonProperty("frase") String quote,
        @JsonProperty("is_authorized") Boolean isAuthorized,
        @JsonProperty("is_published") Boolean isPublished,
        @JsonProperty("fecha") OffsetDateTime date,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("calificacion") Integer rating,
        @JsonProperty("is_approved") Boolean isApproved,
        @JsonProperty("is_featured") Boolean isFeatured,
        @JsonProperty("is_archived") Boolean isArchived
) {
    public static AdminTestimonialResponse fromEntity(Testimonial testimonial) {
        return new AdminTestimonialResponse(testimonial.getId(), testimonial.getAuthorName(),
                testimonial.getAuthorType(), testimonial.getQuote(), testimonial.getIsAuthorized(),
                testimonial.getIsPublished(), testimonial.getCreatedAt(), testimonial.getUpdatedAt(),
                testimonial.getAvatarUrl(), testimonial.getRating(), testimonial.getIsApproved(),
                testimonial.getIsFeatured(), testimonial.getIsArchived());
    }
}
