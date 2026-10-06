package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("BE-26: Endpoints duales /api/recetas y /api/recipes retornan recetas tradicionales sembradas")
    void shouldReturnPublishedRecipesFromDatabase() throws Exception {
        for (String path : new String[]{"/api/recetas", "/api/recipes"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = json(response);
            assertThat(body.isArray()).isTrue();
            assertThat(body.size()).isGreaterThanOrEqualTo(2);

            var first = body.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("titulo")).isTrue();
            assertThat(first.has("slug")).isTrue();
            assertThat(first.has("ingredientes")).isTrue();
            assertThat(first.has("pasos")).isTrue();
        }
    }

    @Test
    @DisplayName("BE-26: Consulta de receta por slug sembrado retorna detalle completo")
    void shouldFindRecipeBySlug() throws Exception {
        // En seed.sql: 'locro-de-papa-con-queso-fresco-de-altura'
        var response = request(HttpMethod.GET, "/api/recetas/locro-de-papa-con-queso-fresco-de-altura", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = json(response);
        assertThat(body.path("slug").asText()).isEqualTo("locro-de-papa-con-queso-fresco-de-altura");
        assertThat(body.path("titulo").asText()).contains("Locro Tradicional");
        assertThat(body.path("porciones").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("BE-26: Consulta de receta inexistente retorna 404 Not Found")
    void shouldReturn404ForUnknownRecipe() throws Exception {
        var response = request(HttpMethod.GET, "/api/recetas/receta-inexistente-123", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-22 / BE-26: Validación de relación N:M entre recetas y productos recomendados (recipe_products)")
    void shouldVerifyRecipeProductsRelation() throws Exception {
        var response = request(HttpMethod.GET, "/api/recetas/locro-de-papa-con-queso-fresco-de-altura", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = json(response);
        String recipeId = body.path("id").asText();

        // Verificar la vinculación en la tabla N:M recipe_products
        Integer linkedProductsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.recipe_products WHERE recipe_id = ?::uuid",
                Integer.class,
                java.util.UUID.fromString(recipeId)
        );
        assertThat(linkedProductsCount).isGreaterThanOrEqualTo(1);

        Boolean isRecommended = jdbc.queryForObject(
                "SELECT is_recommended FROM public.recipe_products WHERE recipe_id = ?::uuid LIMIT 1",
                Boolean.class,
                java.util.UUID.fromString(recipeId)
        );
        assertThat(isRecommended).isTrue();
    }
}
