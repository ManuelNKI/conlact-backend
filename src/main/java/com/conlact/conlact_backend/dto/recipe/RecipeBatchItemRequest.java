package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record RecipeBatchItemRequest(
        String id,
        String slug,

        @NotBlank(message = "El título de la receta es obligatorio")
        @Size(max = 150, message = "El título no puede superar los 150 caracteres")
        @JsonProperty("titulo")
        String title,

        @Size(max = 300, message = "La descripción corta no puede superar los 300 caracteres")
        @JsonProperty("descripcion_corta")
        String shortDescription,

        @Size(max = 60, message = "El tipo de queso no puede superar los 60 caracteres")
        @JsonProperty("tipo_queso")
        String cheeseType,

        @JsonProperty("tiempo_prep")
        Integer prepMinutes,

        @JsonProperty("porciones")
        Integer servings,

        @JsonProperty("imagen_url")
        String imageUrl,

        @JsonProperty("gradient")
        String gradient,

        @JsonProperty("video_url")
        String videoUrl,

        @JsonProperty("pasos")
        List<Object> steps,

        @JsonProperty("ingredientes")
        List<Map<String, Object>> ingredients,

        @JsonProperty("productos_recomendados")
        List<String> recommendedProducts,

        @JsonProperty("orden")
        Integer sortOrder,

        @JsonProperty("publicado")
        Boolean isPublished
) {
}
