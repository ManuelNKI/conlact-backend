package com.conlact.conlact_backend.dto.testimonial;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TestimonialModerationRequest(
        @JsonProperty("is_approved") Boolean isApproved,
        @JsonProperty("is_featured") Boolean isFeatured,
        @JsonProperty("is_archived") Boolean isArchived
) {
}
