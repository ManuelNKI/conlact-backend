package com.conlact.conlact_backend.dto.product.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageCreateRequest(
        @NotBlank(message = "El storage_path de la imagen es obligatorio")
        @Size(max = 1024, message = "El storage_path no puede superar los 1024 caracteres")
        @JsonProperty("storage_path")
        String storagePath,

        @Size(max = 255, message = "El texto alternativo no puede superar los 255 caracteres")
        @JsonProperty("alt_text")
        String altText,

        @Min(value = 0, message = "El orden de visualización no puede ser negativo")
        @JsonProperty("sort_order")
        Integer sortOrder,

        @JsonProperty("is_primary")
        Boolean isPrimary
) {
}
