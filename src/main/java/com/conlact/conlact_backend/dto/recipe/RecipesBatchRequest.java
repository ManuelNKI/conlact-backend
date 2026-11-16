package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record RecipesBatchRequest(
        @Valid
        RecipeBatchConfigRequest config,

        @Valid
        @JsonProperty("recetas")
        List<RecipeBatchItemRequest> recipes,

        @JsonProperty("publicar")
        Boolean publish
) {
}
