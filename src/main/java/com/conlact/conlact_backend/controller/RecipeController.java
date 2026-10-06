package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/recipes", "/api/recetas"})
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getRecipes() {

        return ResponseEntity.ok(
                recipeService.getPublishedRecipes()
        );
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<RecipeResponse> getRecipeByIdentifier(@PathVariable String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            return ResponseEntity.ok(recipeService.getRecipeById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(recipeService.getRecipeBySlug(identifier));
        }
    }
}
