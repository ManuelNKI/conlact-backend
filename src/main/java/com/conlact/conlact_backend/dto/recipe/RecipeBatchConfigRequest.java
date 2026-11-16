package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record RecipeBatchConfigRequest(
        @Size(max = 60, message = "El eyebrow no puede superar los 60 caracteres")
        String eyebrow,

        @Size(max = 150, message = "El título no puede superar los 150 caracteres")
        @JsonProperty("titulo")
        String title,

        @Size(max = 300, message = "El subtítulo no puede superar los 300 caracteres")
        @JsonProperty("subtitulo")
        String subtitle
) {
}
