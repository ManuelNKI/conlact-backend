package com.conlact.conlact_backend.dto.training;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TrainingContentPatchRequest(
        @Size(min = 3, max = 150, message = "El título debe tener entre 3 y 150 caracteres")
        @JsonProperty("titulo")
        String title,

        @Size(max = 300, message = "La descripción no puede superar los 300 caracteres")
        @JsonProperty("descripcion")
        String description,

        @Pattern(regexp = "^(video|texto|enlace|documento)$", message = "Tipo inválido. Valores permitidos: video, texto, enlace, documento")
        @JsonProperty("tipo")
        String type,

        @Size(max = 2048, message = "La URL no puede superar los 2048 caracteres")
        @JsonProperty("url")
        String url,

        @Size(max = 5000, message = "El cuerpo no puede superar los 5000 caracteres")
        @JsonProperty("cuerpo")
        String body,

        @JsonProperty("publicado")
        Boolean isPublished,

        @JsonProperty("mover")
        String move
) {
}
