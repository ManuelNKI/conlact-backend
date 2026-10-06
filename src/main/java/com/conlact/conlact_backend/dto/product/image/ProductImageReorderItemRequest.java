package com.conlact.conlact_backend.dto.product.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductImageReorderItemRequest(
        @NotNull(message = "El ID de la imagen es obligatorio")
        UUID id,

        @NotNull(message = "El orden de visualización es obligatorio")
        @Min(value = 0, message = "El orden de visualización no puede ser negativo")
        @JsonProperty("sort_order")
        Integer sortOrder
) {
}
