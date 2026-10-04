package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RecipeDetailResponse {

    private UUID id;

    @JsonProperty("titulo")
    private String title;

    @JsonProperty("ingredientes")
    private List<RecipeIngredientResponse> ingredients;

    @JsonProperty("pasos")
    private List<RecipeStepResponse> steps;

    @JsonProperty("producto_slug")
    private String productSlug;

    @JsonProperty("tipo_queso")
    private String cheeseType;

    @JsonProperty("tiempo_prep")
    private Integer preparationTime;

    @JsonProperty("quesos_recomendados")
    private List<RecommendedProductResponse> recommendedProducts;
}