package com.conlact.conlact_backend.dto.product.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductImageReorderRequest(
        @NotEmpty(message = "La lista de imágenes para reordenar no puede estar vacía")
        @JsonProperty("images")
        List<@Valid ProductImageReorderItemRequest> images
) {
}
