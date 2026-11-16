package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record RecipesBatchResponse(
        RecipeBatchConfigRequest config,
        @JsonProperty("recetas") List<RecipeResponse> recipes
) {
}
