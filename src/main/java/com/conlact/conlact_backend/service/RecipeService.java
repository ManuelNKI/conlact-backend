package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.cms.PageContentResponse;
import com.conlact.conlact_backend.dto.cms.PageContentSaveRequest;
import com.conlact.conlact_backend.dto.recipe.*;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.Recipe;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.RecipeRepository;
import com.conlact.conlact_backend.storage.IStorage;
import com.conlact.conlact_backend.util.SlugUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ProductRepository productRepository;
    private final EditorialContentService editorialContentService;
    private final IStorage storage;
    private final String recipeBucket;

    public RecipeService(RecipeRepository recipeRepository,
                         ProductRepository productRepository,
                         EditorialContentService editorialContentService,
                         IStorage storage,
                         @Value("${app.supabase.storage.recipe-bucket:recipe-images}") String recipeBucket) {
        this.recipeRepository = recipeRepository;
        this.productRepository = productRepository;
        this.editorialContentService = editorialContentService;
        this.storage = storage;
        this.recipeBucket = recipeBucket;
    }

    // ==========================================
    // Endpoints Públicos
    // ==========================================

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

    // ==========================================
    // Endpoints Administrativos (Por Lote y CRUD)
    // ==========================================

    @Transactional(readOnly = true)
    public RecipesBatchResponse getAdminRecipesBatch(boolean publicOnly) {
        PageContentResponse pageConfig = editorialContentService.getPublicPage("recetas");
        RecipeBatchConfigRequest config = new RecipeBatchConfigRequest(
                pageConfig.eyebrow(),
                pageConfig.title(),
                pageConfig.subtitle()
        );

        List<Recipe> recipes = publicOnly
                ? recipeRepository.findByIsPublishedTrue()
                : recipeRepository.findAll();

        List<RecipeResponse> recipeResponses = recipes.stream()
                .sorted(Comparator.comparing(Recipe::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Recipe::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();

        return RecipesBatchResponse.builder()
                .config(config)
                .recipes(recipeResponses)
                .build();
    }

    @Transactional
    public RecipesBatchResponse saveRecipesBatch(RecipesBatchRequest request) {
        if (request.config() != null) {
            editorialContentService.savePage("recetas", new PageContentSaveRequest(
                    request.config().eyebrow(),
                    request.config().title() != null ? request.config().title() : "Recetas Tradicionales",
                    request.config().subtitle(),
                    new HashMap<>(),
                    null,
                    null
            ));
        }

        if (request.recipes() != null) {
            int orderCounter = 0;
            for (RecipeBatchItemRequest item : request.recipes()) {
                if (item == null || item.title() == null || item.title().isBlank()) continue;

                Recipe recipe = findExistingRecipe(item.id(), item.slug(), item.title());
                if (recipe == null) {
                    recipe = new Recipe();
                    recipe.setSlug(resolveUniqueSlug(item.slug(), item.title(), null));
                } else if (item.slug() != null && !item.slug().isBlank()) {
                    recipe.setSlug(resolveUniqueSlug(item.slug(), item.title(), recipe.getId()));
                }

                recipe.setTitle(item.title().trim());
                recipe.setShortDescription(item.shortDescription());
                recipe.setCheeseType(item.cheeseType());
                recipe.setPrepMinutes(item.prepMinutes() != null && item.prepMinutes() > 0 ? item.prepMinutes() : 30);
                recipe.setServings(item.servings() != null && item.servings() > 0 ? item.servings() : 4);
                recipe.setImageStoragePath(item.imageUrl());
                recipe.setVideoUrl(item.videoUrl());
                recipe.setGradient(item.gradient() != null ? item.gradient() : "linear-gradient(135deg,#d4a373,#8a5a3a)");
                recipe.setSortOrder(item.sortOrder() != null ? item.sortOrder() : orderCounter++);

                if (request.publish() != null) {
                    recipe.setIsPublished(request.publish());
                } else if (item.isPublished() != null) {
                    recipe.setIsPublished(item.isPublished());
                } else if (recipe.getIsPublished() == null) {
                    recipe.setIsPublished(true);
                }

                if (item.steps() != null) {
                    recipe.setSteps(normalizeSteps(item.steps()));
                }

                if (item.ingredients() != null) {
                    recipe.setIngredients(item.ingredients());
                }

                if (item.recommendedProducts() != null) {
                    Set<Product> products = resolveProducts(item.recommendedProducts());
                    recipe.setProducts(products);
                }

                recipeRepository.save(recipe);
            }
        }

        return getAdminRecipesBatch(false);
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipeAdminById(UUID id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con ID: " + id));
        return toResponse(recipe);
    }

    @Transactional
    public RecipeResponse createRecipe(RecipeBatchItemRequest request) {
        String slug = resolveUniqueSlug(request.slug(), request.title(), null);

        Recipe recipe = Recipe.builder()
                .title(request.title().trim())
                .slug(slug)
                .shortDescription(request.shortDescription())
                .cheeseType(request.cheeseType())
                .prepMinutes(request.prepMinutes() != null && request.prepMinutes() > 0 ? request.prepMinutes() : 30)
                .servings(request.servings() != null && request.servings() > 0 ? request.servings() : 4)
                .imageStoragePath(request.imageUrl())
                .videoUrl(request.videoUrl())
                .gradient(request.gradient() != null ? request.gradient() : "linear-gradient(135deg,#d4a373,#8a5a3a)")
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .isPublished(Boolean.TRUE.equals(request.isPublished()))
                .steps(request.steps() != null ? normalizeSteps(request.steps()) : new ArrayList<>())
                .ingredients(request.ingredients() != null ? request.ingredients() : new ArrayList<>())
                .build();

        if (request.recommendedProducts() != null) {
            recipe.setProducts(resolveProducts(request.recommendedProducts()));
        }

        Recipe saved = recipeRepository.save(recipe);
        return toResponse(saved);
    }

    @Transactional
    public RecipeResponse updateRecipe(UUID id, RecipeBatchItemRequest request) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con ID: " + id));

        String slug = resolveUniqueSlug(request.slug(), request.title(), recipe.getId());

        recipe.setTitle(request.title().trim());
        recipe.setSlug(slug);
        recipe.setShortDescription(request.shortDescription());
        recipe.setCheeseType(request.cheeseType());
        if (request.prepMinutes() != null && request.prepMinutes() > 0) {
            recipe.setPrepMinutes(request.prepMinutes());
        }
        if (request.servings() != null && request.servings() > 0) {
            recipe.setServings(request.servings());
        }
        if (request.imageUrl() != null) {
            recipe.setImageStoragePath(request.imageUrl());
        }
        if (request.videoUrl() != null) {
            recipe.setVideoUrl(request.videoUrl());
        }
        if (request.gradient() != null) {
            recipe.setGradient(request.gradient());
        }
        if (request.sortOrder() != null) {
            recipe.setSortOrder(request.sortOrder());
        }
        if (request.isPublished() != null) {
            recipe.setIsPublished(request.isPublished());
        }
        if (request.steps() != null) {
            recipe.setSteps(normalizeSteps(request.steps()));
        }
        if (request.ingredients() != null) {
            recipe.setIngredients(request.ingredients());
        }
        if (request.recommendedProducts() != null) {
            recipe.setProducts(resolveProducts(request.recommendedProducts()));
        }

        Recipe updated = recipeRepository.save(recipe);
        return toResponse(updated);
    }

    @Transactional
    public void deleteRecipe(UUID id) {
        if (!recipeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Receta no encontrada con ID: " + id);
        }
        recipeRepository.deleteById(id);
    }

    @Transactional
    public RecipeResponse changePublication(UUID id, Boolean published) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada con ID: " + id));

        boolean newStatus = published != null ? published : !Boolean.TRUE.equals(recipe.getIsPublished());
        recipe.setIsPublished(newStatus);
        Recipe updated = recipeRepository.save(recipe);
        return toResponse(updated);
    }

    // ==========================================
    // Métodos Auxiliares
    // ==========================================

    private Recipe findExistingRecipe(String idStr, String slug, String title) {
        if (idStr != null && !idStr.isBlank()) {
            try {
                UUID id = UUID.fromString(idStr);
                Optional<Recipe> byId = recipeRepository.findById(id);
                if (byId.isPresent()) return byId.get();
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (slug != null && !slug.isBlank()) {
            Optional<Recipe> bySlug = recipeRepository.findBySlug(slug);
            if (bySlug.isPresent()) return bySlug.get();
        }
        return null;
    }

    private String resolveUniqueSlug(String requestedSlug, String title, UUID existingId) {
        String base = (requestedSlug != null && !requestedSlug.isBlank())
                ? SlugUtils.toSlug(requestedSlug)
                : SlugUtils.toSlug(title);

        String candidate = base;
        int count = 2;
        while (true) {
            Optional<Recipe> existing = recipeRepository.findBySlug(candidate);
            if (existing.isEmpty() || (existingId != null && existing.get().getId().equals(existingId))) {
                return candidate;
            }
            candidate = base + "-" + count++;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeSteps(List<Object> stepsRaw) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        int stepNumber = 1;
        for (Object s : stepsRaw) {
            if (s instanceof Map<?, ?> map) {
                normalized.add((Map<String, Object>) map);
            } else if (s instanceof String str && !str.isBlank()) {
                Map<String, Object> stepObj = new HashMap<>();
                stepObj.put("step", stepNumber++);
                stepObj.put("instruction", str.trim());
                normalized.add(stepObj);
            }
        }
        return normalized;
    }

    private Set<Product> resolveProducts(List<String> productIdsOrSlugs) {
        Set<Product> products = new HashSet<>();
        for (String idOrSlug : productIdsOrSlugs) {
            if (idOrSlug == null || idOrSlug.isBlank()) continue;
            try {
                UUID id = UUID.fromString(idOrSlug);
                productRepository.findById(id).ifPresent(products::add);
            } catch (IllegalArgumentException e) {
                // Ignore or lookup by other means
            }
        }
        return products;
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
