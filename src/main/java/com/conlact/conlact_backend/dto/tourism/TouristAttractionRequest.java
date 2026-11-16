package com.conlact.conlact_backend.dto.tourism;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TouristAttractionRequest(
        @NotBlank(message = "El nombre del atractivo es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        @JsonProperty("nombre")
        String name,

        @Size(max = 600, message = "La descripción no puede superar los 600 caracteres")
        @JsonProperty("descripcion")
        String description,

        @Size(max = 60, message = "La duración no puede superar los 60 caracteres")
        @JsonProperty("duracion")
        String duration,

        @Size(max = 60, message = "La dificultad no puede superar los 60 caracteres")
        @JsonProperty("dificultad")
        String difficulty,

        @JsonProperty("foto_url")
        String photoUrl,

        @Size(max = 2048, message = "La URL del video no puede superar los 2048 caracteres")
        @JsonProperty("video_url")
        String videoUrl,

        @JsonProperty("asociacion_id")
        UUID associationId,

        @JsonProperty("tipo")
        String attractionType,

        @JsonProperty("lat")
        BigDecimal latitude,

        @JsonProperty("lng")
        BigDecimal longitude,

        @JsonProperty("requiere_confirmacion")
        Boolean requiresConfirmation,

        @JsonProperty("condiciones_acceso")
        String accessConditions,

        @JsonProperty("is_published")
        Boolean isPublished
) {
}
