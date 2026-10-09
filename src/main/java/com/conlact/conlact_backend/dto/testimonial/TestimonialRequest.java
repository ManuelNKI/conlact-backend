package com.conlact.conlact_backend.dto.testimonial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestimonialRequest(
        @JsonProperty("autor_nombre")
        @JsonAlias("autor")
        @NotBlank(message = "El nombre del autor es obligatorio")
        @Size(max = 150, message = "El nombre del autor no puede superar los 150 caracteres")
        String authorName,
        @JsonProperty("autor_tipo")
        @JsonAlias("rol")
        @Size(max = 150, message = "El tipo de autor no puede superar los 150 caracteres")
        String authorType,
        @JsonProperty("frase")
        @JsonAlias("comentario")
        @NotBlank(message = "La frase del testimonio es obligatoria")
        @Size(max = 4000, message = "La frase no puede superar los 4000 caracteres")
        String quote,
        @JsonProperty("is_authorized") Boolean isAuthorized,
        @JsonProperty("is_published") Boolean isPublished,
        @JsonProperty("avatar_url") @JsonAlias("avatar")
        @Size(max = 2048, message = "La URL del avatar no puede superar los 2048 caracteres") String avatarUrl,
        @JsonProperty("calificacion") @JsonAlias("rating")
        @Min(value = 1, message = "La calificación mínima es 1")
        @Max(value = 5, message = "La calificación máxima es 5") Integer rating,
        @JsonProperty("is_approved") Boolean isApproved,
        @JsonProperty("is_featured") Boolean isFeatured,
        @JsonProperty("is_archived") Boolean isArchived
) {
    public TestimonialRequest(String authorName, String authorType, String quote, Boolean isAuthorized, Boolean isPublished) {
        this(authorName, authorType, quote, isAuthorized, isPublished, null, null, null, null, null);
    }
}
