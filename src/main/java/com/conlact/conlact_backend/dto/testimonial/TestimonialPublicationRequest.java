package com.conlact.conlact_backend.dto.testimonial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record TestimonialPublicationRequest(
        @JsonProperty("is_published")
        @NotNull(message = "El estado de publicación es obligatorio")
        Boolean isPublished
) {
}
