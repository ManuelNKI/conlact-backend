package com.conlact.conlact_backend.dto.product;

import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdminProductRequest(
        @JsonProperty("asociacion_id") UUID associationId,
        @JsonProperty("categoria_id") UUID categoryId,
        @JsonProperty("nombre")
        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,
        @Size(max = 160, message = "El slug no puede superar los 160 caracteres") String slug,
        @JsonProperty("tipo_queso")
        @Size(max = 150, message = "El tipo de queso no puede superar los 150 caracteres")
        String cheeseType,
        @JsonProperty("descripcion_corta")
        @Size(max = 500, message = "La descripción corta no puede superar los 500 caracteres")
        String shortDescription,
        @JsonProperty("descripcion")
        @Size(max = 10000, message = "La descripción no puede superar los 10000 caracteres")
        String description,
        @JsonProperty("origen")
        @Size(max = 500, message = "El origen no puede superar los 500 caracteres")
        String originText,
        @JsonProperty("conservacion")
        @Size(max = 2000, message = "La conservación no puede superar los 2000 caracteres")
        String conservation,
        ProductStatus status,
        @JsonProperty("is_featured") Boolean isFeatured
) {
}
