package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.recipe.RecipeResponse;
import com.conlact.conlact_backend.exception.GlobalExceptionHandler;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.service.RecipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecipeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private RecipeController recipeController;

    private UUID recipeId;
    private RecipeResponse sampleRecipe;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(recipeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        recipeId = UUID.randomUUID();
        sampleRecipe = RecipeResponse.builder()
                .id(recipeId)
                .slug("locro-papa-queso-altura")
                .title("Locro Tradicional de Papa con Queso Fresco")
                .shortDescription("Plato típico de la sierra ecuatoriana con queso tierno de Pilahuín")
                .prepMinutes(45)
                .servings(4)
                .ingredients(List.of(Map.of("item", "Papas chola", "quantity", "1 kg")))
                .steps(List.of(Map.of("step", 1, "instruction", "Hacer refrito con achiote")))
                .imageUrl("https://example.com/locro.webp")
                .isPublished(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/recetas retorna listado de recetas publicadas con HTTP 200")
    void shouldReturnPublishedRecipesSpanishRoute() throws Exception {
        when(recipeService.getPublishedRecipes()).thenReturn(List.of(sampleRecipe));

        mockMvc.perform(get("/api/recetas"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(recipeId.toString()))
                .andExpect(jsonPath("$[0].slug").value("locro-papa-queso-altura"))
                .andExpect(jsonPath("$[0].titulo").value("Locro Tradicional de Papa con Queso Fresco"))
                .andExpect(jsonPath("$[0].porciones").value(4));
    }

    @Test
    @DisplayName("GET /api/recipes (alias en inglés) responde idéntico a la ruta en español")
    void shouldReturnPublishedRecipesEnglishRoute() throws Exception {
        when(recipeService.getPublishedRecipes()).thenReturn(List.of(sampleRecipe));

        mockMvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(recipeId.toString()));
    }

    @Test
    @DisplayName("GET /api/recetas/{slug} retorna el detalle de la receta por su slug")
    void shouldReturnRecipeBySlug() throws Exception {
        when(recipeService.getRecipeBySlug("locro-papa-queso-altura")).thenReturn(sampleRecipe);

        mockMvc.perform(get("/api/recetas/locro-papa-queso-altura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("locro-papa-queso-altura"))
                .andExpect(jsonPath("$.titulo").value("Locro Tradicional de Papa con Queso Fresco"));
    }

    @Test
    @DisplayName("GET /api/recetas/{id} con UUID inexistente retorna 404 Not Found")
    void shouldReturn404WhenRecipeNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(recipeService.getRecipeById(unknownId)).thenThrow(new ResourceNotFoundException("Receta no encontrada"));

        mockMvc.perform(get("/api/recetas/{id}", unknownId))
                .andExpect(status().isNotFound());
    }
}
