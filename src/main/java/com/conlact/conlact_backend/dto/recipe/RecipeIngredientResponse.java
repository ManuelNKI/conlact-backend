package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeIngredientResponse {

    private String item;

    @JsonProperty("cantidad")
    private String quantity;
}