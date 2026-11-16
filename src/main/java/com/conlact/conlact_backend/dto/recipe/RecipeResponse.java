package com.conlact.conlact_backend.dto.recipe;

import com.conlact.conlact_backend.entity.Recipe;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Collections;
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
        @JsonProperty("tiempo_prep") Integer prepMinutesAlias,
        @JsonProperty("porciones") Integer servings,
        @JsonProperty("tipo_queso") String cheeseType,
        @JsonProperty("gradient") String gradient,
        @JsonProperty("video_url") String videoUrl,
        @JsonProperty("orden") Integer sortOrder,
        @JsonProperty("ingredientes") List<Map<String, Object>> ingredients,
        @JsonProperty("pasos") List<Map<String, Object>> steps,
        @JsonProperty("imagen_url") String imageUrl,
        @JsonProperty("productos_recomendados") List<String> recommendedProducts,
        @JsonProperty("is_published") Boolean isPublished,
        @JsonProperty("publicado") Boolean publishedAlias
) {
    public static RecipeResponse fromEntity(Recipe recipe, String publicImageUrl) {
        List<String> prodIds = recipe.getProducts() == null ? Collections.emptyList() :
                recipe.getProducts().stream().map(p -> p.getId().toString()).toList();

        return RecipeResponse.builder()
                .id(recipe.getId())
                .slug(recipe.getSlug())
                .title(recipe.getTitle())
                .shortDescription(recipe.getShortDescription())
                .prepMinutes(recipe.getPrepMinutes())
                .prepMinutesAlias(recipe.getPrepMinutes())
                .servings(recipe.getServings())
                .cheeseType(recipe.getCheeseType())
                .gradient(recipe.getGradient())
                .videoUrl(recipe.getVideoUrl())
                .sortOrder(recipe.getSortOrder())
                .ingredients(recipe.getIngredients())
                .steps(recipe.getSteps())
                .imageUrl(publicImageUrl != null ? publicImageUrl : recipe.getImageStoragePath())
                .recommendedProducts(prodIds)
                .isPublished(recipe.getIsPublished())
                .publishedAlias(recipe.getIsPublished())
                .build();
    }
}
