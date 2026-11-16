package com.conlact.conlact_backend.dto.training;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrainingSectionCreateRequest(
        @NotBlank(message = "El título de la sección es obligatorio")
        @Size(min = 3, max = 150, message = "El título de la sección debe tener entre 3 y 150 caracteres")
        @JsonProperty("titulo")
        String title,

        @Size(max = 300, message = "La descripción no puede superar los 300 caracteres")
        @JsonProperty("descripcion")
        String description,

        @JsonProperty("publicado")
        Boolean isPublished
) {
}
