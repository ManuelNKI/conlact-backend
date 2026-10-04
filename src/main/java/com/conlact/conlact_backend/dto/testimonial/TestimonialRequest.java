package com.conlact.conlact_backend.dto.testimonial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestimonialRequest(
        @JsonProperty("autor_nombre")
        @NotBlank(message = "El nombre del autor es obligatorio")
        @Size(max = 150, message = "El nombre del autor no puede superar los 150 caracteres")
        String authorName,
        @JsonProperty("autor_tipo")
        @Size(max = 150, message = "El tipo de autor no puede superar los 150 caracteres")
        String authorType,
        @JsonProperty("frase")
        @NotBlank(message = "La frase del testimonio es obligatoria")
        @Size(max = 4000, message = "La frase no puede superar los 4000 caracteres")
        String quote,
        @JsonProperty("is_authorized") Boolean isAuthorized,
        @JsonProperty("is_published") Boolean isPublished
) {
}
