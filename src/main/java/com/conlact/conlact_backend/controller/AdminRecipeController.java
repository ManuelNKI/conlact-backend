package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.entity.Recipe;
import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.repository.RecipeRepository;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/recetas") @PreAuthorize("hasRole('ADMIN')")
public class AdminRecipeController {
    private final RecipeRepository repository;
    private final com.conlact.conlact_backend.service.ContentMediaService media;
    public record RecipeRequest(String id, @NotBlank String slug, @NotBlank String titulo,
        String descripcion_corta, @Min(1) Integer tiempo_prep, @Min(1) Integer porciones,
        String imagen_url, String video_url, String tipo_queso, Integer orden,
        Boolean publicado, @NotNull List<String> pasos,
        List<Map<String,Object>> ingredientes) {}
    public record SyncRequest(@NotNull @Valid List<RecipeRequest> recetas, List<UUID> eliminadas) {}
    @GetMapping public List<RecipeResponse> list() {
        return repository.findAll().stream().map(r -> RecipeResponse.fromEntity(r, r.getImageStoragePath())).toList();
    }
    @PostMapping @Transactional public RecipeResponse create(@Valid @RequestBody RecipeRequest data) {
        return save(new Recipe(), data);
    }
    @PutMapping("/{id}") @Transactional public RecipeResponse update(@PathVariable UUID id, @Valid @RequestBody RecipeRequest data) {
        return save(find(id), data);
    }
    @DeleteMapping("/{id}") @Transactional public void delete(@PathVariable UUID id) { repository.delete(find(id)); }
    @PostMapping("/sync") @Transactional public List<RecipeResponse> sync(@Valid @RequestBody SyncRequest data) {
        // Explicit removals only: never delete recipes merely absent from a public listing.
        if (data.eliminadas() != null) data.eliminadas().forEach(id -> repository.delete(find(id)));
        return data.recetas().stream().map(r -> save(existing(r.id()),r)).toList();
    }
    private Recipe find(UUID id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada")); }
    private Recipe existing(String id) {
        if(id==null || id.isBlank()) return new Recipe();
        try { return find(UUID.fromString(id)); } catch (IllegalArgumentException e) { return new Recipe(); }
    }
    private RecipeResponse save(Recipe recipe, RecipeRequest data) {
        recipe.setSlug(data.slug()); recipe.setTitle(data.titulo()); recipe.setShortDescription(data.descripcion_corta());
        recipe.setPrepMinutes(data.tiempo_prep()); recipe.setServings(data.porciones());
        recipe.setImageStoragePath(media.image(data.imagen_url(),"recipe-images")); recipe.setVideoUrl(data.video_url());
        recipe.setCheeseType(data.tipo_queso()); recipe.setDisplayOrder(data.orden()==null?0:data.orden());
        recipe.setIsPublished(Boolean.TRUE.equals(data.publicado()));
        recipe.setSteps(data.pasos().stream().map(step -> Map.<String,Object>of("instruction",step)).toList());
        if(data.ingredientes()!=null) recipe.setIngredients(data.ingredientes());
        return RecipeResponse.fromEntity(repository.saveAndFlush(recipe), recipe.getImageStoragePath());
    }
}
