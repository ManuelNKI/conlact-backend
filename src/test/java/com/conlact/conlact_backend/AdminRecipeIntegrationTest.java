package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRecipeIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe sincronizar atómicamente por lote recetas tradicionales y configuración editorial")
    void shouldSaveAndSyncBatchRecipes() throws Exception {
        Map<String, Object> batchPayload = Map.of(
                "config", Map.of(
                        "eyebrow", "Gastronomía Andina Tungurahua",
                        "titulo", "Recetas Tradicionales de Páramo",
                        "subtitulo", "Platos elaborados con quesos auténticos"
                ),
                "publicar", true,
                "recetas", List.of(
                        Map.of(
                                "titulo", "Empanadas de Viento con Queso Mulanleo",
                                "descripcion_corta", "Crocantes empanadas de viento",
                                "tipo_queso", "Queso de Hoja",
                                "tiempo_prep", 35,
                                "porciones", 6,
                                "imagen_url", "https://example.com/empanadas.jpg",
                                "video_url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                                "pasos", List.of("Amasar la harina con manteca", "Rellenar con queso de hoja", "Freír en aceite caliente"),
                                "ingredientes", List.of(Map.of("item", "Harina de trigo", "quantity", "500g")),
                                "publicado", true
                        )
                )
        );

        ResponseEntity<String> postRes = request(HttpMethod.POST, "/api/admin/recetas", batchPayload, token);
        assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = json(postRes);
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("data").get("config").get("titulo").asText()).isEqualTo("Recetas Tradicionales de Páramo");
        assertThat(json.get("data").get("recetas").size()).isGreaterThanOrEqualTo(1);

        // Comprobar que endpoint público /api/recipes o /api/recetas devuelva la receta
        ResponseEntity<String> getRes = request(HttpMethod.GET, "/api/recetas", null, null);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode getJson = json(getRes);
        boolean found = false;
        for (JsonNode r : getJson) {
            if ("Empanadas de Viento con Queso Mulanleo".equals(r.get("titulo").asText())) {
                found = true;
                assertThat(r.get("tipo_queso").asText()).isEqualTo("Queso de Hoja");
                assertThat(r.get("tiempo_preparacion_minutos").asInt()).isEqualTo(35);
                assertThat(r.get("video_url").asText()).contains("youtube.com");
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Debe permitir CRUD individual y alternar publicación de receta")
    void shouldPerformIndividualRecipeCrudAndTogglePublication() throws Exception {
        Map<String, Object> createReq = Map.of(
                "titulo", "Locro de Papa con Queso Tierno",
                "descripcion_corta", "Sopa tradicional con papa chola y queso fresco",
                "tipo_queso", "Queso Fresco",
                "tiempo_prep", 45,
                "porciones", 4,
                "publicado", true
        );

        ResponseEntity<String> createRes = request(HttpMethod.POST, "/api/admin/recetas/individual", createReq, token);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String recipeId = json(createRes).get("id").asText();

        // Alternar publicación a false
        ResponseEntity<String> toggleRes = request(HttpMethod.PATCH, "/api/admin/recetas/" + recipeId + "/publicacion", Map.of("publicado", false), token);
        assertThat(toggleRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(toggleRes).get("is_published").asBoolean()).isFalse();

        // Borrar receta
        ResponseEntity<String> delRes = request(HttpMethod.DELETE, "/api/admin/recetas/" + recipeId, null, token);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
