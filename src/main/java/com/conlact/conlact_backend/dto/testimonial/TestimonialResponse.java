package com.conlact.conlact_backend.dto.testimonial;

import com.conlact.conlact_backend.entity.Testimonial;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TestimonialResponse(
        UUID id,
        @JsonProperty("autor_nombre") String authorName,
        @JsonProperty("autor_tipo") String authorType,
        @JsonProperty("frase") String quote,
        @JsonProperty("fecha") OffsetDateTime date,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("calificacion") Integer rating,
        @JsonProperty("is_featured") Boolean isFeatured
) {
    public static TestimonialResponse fromEntity(Testimonial testimonial) {
        return new TestimonialResponse(testimonial.getId(), testimonial.getAuthorName(),
                testimonial.getAuthorType(), testimonial.getQuote(), testimonial.getCreatedAt(),
                testimonial.getAvatarUrl(), testimonial.getRating(), testimonial.getIsFeatured());
    }
}
