package com.conlact.conlact_backend.dto.recipe;

import com.conlact.conlact_backend.entity.Recipe;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecipeResponse(
        UUID id,
        String slug,
        @JsonProperty("titulo") String title,
        @JsonProperty("descripcion_corta") String shortDescription,
        @JsonProperty("tiempo_preparacion_minutos") Integer prepMinutes,
        @JsonProperty("porciones") Integer servings,
        @JsonProperty("ingredientes") List<Map<String, Object>> ingredients,
        @JsonProperty("pasos") List<Map<String, Object>> steps,
        @JsonProperty("imagen_url") String imageUrl,
        @JsonProperty("is_published") Boolean isPublished
) {
    public static RecipeResponse fromEntity(Recipe recipe, String publicImageUrl) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .slug(recipe.getSlug())
                .title(recipe.getTitle())
                .shortDescription(recipe.getShortDescription())
                .prepMinutes(recipe.getPrepMinutes())
                .servings(recipe.getServings())
                .ingredients(recipe.getIngredients())
                .steps(recipe.getSteps())
                .imageUrl(publicImageUrl != null ? publicImageUrl : recipe.getImageStoragePath())
                .isPublished(recipe.getIsPublished())
                .build();
    }
}
