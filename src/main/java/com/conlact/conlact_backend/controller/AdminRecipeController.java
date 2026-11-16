package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.recipe.RecipeBatchItemRequest;
import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.dto.recipe.RecipesBatchRequest;
import com.conlact.conlact_backend.dto.recipe.RecipesBatchResponse;
import com.conlact.conlact_backend.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/recetas", "/api/admin/recipes"})
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@RequiredArgsConstructor
public class AdminRecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<RecipesBatchResponse> getRecipesBatch(
            @RequestParam(name = "publico", defaultValue = "false") boolean publicOnly) {
        return ResponseEntity.ok(recipeService.getAdminRecipesBatch(publicOnly));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveRecipesBatch(
            @Valid @RequestBody RecipesBatchRequest request) {
        RecipesBatchResponse response = recipeService.saveRecipesBatch(request);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable UUID id) {
        return ResponseEntity.ok(recipeService.getRecipeAdminById(id));
    }

    @PostMapping("/individual")
    public ResponseEntity<RecipeResponse> createRecipe(
            @Valid @RequestBody RecipeBatchItemRequest request) {
        RecipeResponse response = recipeService.createRecipe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable UUID id,
            @Valid @RequestBody RecipeBatchItemRequest request) {
        RecipeResponse response = recipeService.updateRecipe(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping({"/{id}/publicacion", "/{id}/publish"})
    public ResponseEntity<RecipeResponse> changePublication(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Boolean published = (body != null && body.containsKey("publicado")) ? body.get("publicado") : null;
        return ResponseEntity.ok(recipeService.changePublication(id, published));
    }
}
