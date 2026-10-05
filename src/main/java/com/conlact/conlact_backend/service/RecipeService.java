package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.recipe.RelatedProductResponse;
import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.Recipe;
import com.conlact.conlact_backend.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public List<RecipeResponse> getPublishedRecipes() {

        return recipeRepository.findByIsPublishedTrue()
                .stream()
                .map(this::toRecipeResponse)
                .toList();
    }

    public RecipeResponse getPublishedRecipeById(UUID recipeId) {

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Receta no encontrada"
                ));

        if (!Boolean.TRUE.equals(recipe.getIsPublished())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Receta no encontrada"
            );
        }

        return toRecipeResponse(recipe);
    }

    private RecipeResponse toRecipeResponse(Recipe recipe) {

        List<Product> products = recipe.getProducts()
                .stream()
                .toList();

        Product mainProduct = products.isEmpty()
                ? null
                : products.get(0);

        return RecipeResponse.builder()
                .id(recipe.getId())
                .title(recipe.getTitle())
                .slug(recipe.getSlug())
                .cheeseType(
                        mainProduct != null
                                ? mainProduct.getCheeseType()
                                : null
                )
                .preparationTime(recipe.getPrepMinutes())
                .servings(recipe.getServings())
                .productSlug(
                        mainProduct != null
                                ? mainProduct.getSlug()
                                : null
                )
                .imageUrl(buildImageUrl(recipe.getImageStoragePath()))
                .pasos(toSteps(recipe.getSteps()))
                .ingredientes(toIngredients(recipe.getIngredients()))
                .relatedProducts(
                        toRelatedProducts(recipe.getId())
                )
                .build();
    }

    private List<String> toIngredients(
            List<Map<String, Object>> ingredients
    ) {

        return ingredients.stream()
                .map(ingredient -> {

                    String quantity =
                            (String) ingredient.get("quantity");

                    String item =
                            (String) ingredient.get("item");

                    if (quantity == null || quantity.isBlank()) {
                        return item;
                    }

                    return quantity + " " + item;
                })
                .toList();
    }

    private List<String> toSteps(
            List<Map<String, Object>> steps
    ) {

        return steps.stream()
                .map(step ->
                        (String) step.get("instruction")
                )
                .toList();
    }

    private List<RelatedProductResponse> toRelatedProducts(
            UUID recipeId
    ) {

        return recipeRepository
                .findRelatedProductsByRecipeId(recipeId)
                .stream()
                .map(row -> RelatedProductResponse.builder()
                        .productId((UUID) row[0])
                        .nombre((String) row[1])
                        .precio((BigDecimal) row[2])
                        .recommended((Boolean) row[3])
                        .build()
                )
                .toList();
    }

    private String buildImageUrl(String storagePath) {

        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }

        return storagePath;
    }
}