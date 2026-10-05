package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.entity.Recipe;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.RecipeRepository;
import com.conlact.conlact_backend.storage.IStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IStorage storage;
    private final String recipeBucket;

    public RecipeService(RecipeRepository recipeRepository,
                         IStorage storage,
                         @Value("${app.supabase.storage.recipe-bucket:recipe-images}") String recipeBucket) {
        this.recipeRepository = recipeRepository;
        this.storage = storage;
        this.recipeBucket = recipeBucket;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> getPublishedRecipes() {
        return recipeRepository.findByIsPublishedTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipeBySlug(String slug) {
        Recipe recipe = recipeRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con slug: " + slug));

        if (!Boolean.TRUE.equals(recipe.getIsPublished())) {
            throw new ResourceNotFoundException("Receta no disponible o no publicada");
        }

        return toResponse(recipe);
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipeById(UUID id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con ID: " + id));

        if (!Boolean.TRUE.equals(recipe.getIsPublished())) {
            throw new ResourceNotFoundException("Receta no disponible o no publicada");
        }

        return toResponse(recipe);
    }

    private RecipeResponse toResponse(Recipe recipe) {
        String publicImageUrl = null;
        if (recipe.getImageStoragePath() != null && !recipe.getImageStoragePath().isBlank()) {
            if (recipe.getImageStoragePath().startsWith("http://") || recipe.getImageStoragePath().startsWith("https://")
                    || recipe.getImageStoragePath().startsWith("/")) {
                publicImageUrl = recipe.getImageStoragePath();
            } else {
                publicImageUrl = storage.getPublicUrl(recipeBucket, recipe.getImageStoragePath());
            }
        }

        return RecipeResponse.fromEntity(recipe, publicImageUrl);
    }
}
