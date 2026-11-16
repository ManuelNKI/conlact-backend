package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTouristAttractionIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe crear, actualizar y retornar foto_url, duracion y dificultad en endpoints público y administrativo")
    void shouldCreateAndUpdateAttractionWithAllFields() throws Exception {
        Map<String, Object> createReq = Map.of(
                "nombre", "Ruta de los Páramos de Carihuairazo",
                "descripcion", "Senderismo y avistamiento de vicuñas en las faldas del volcán",
                "duracion", "4 horas",
                "dificultad", "Moderada",
                "foto_url", "https://example.com/carihuairazo.jpg",
                "video_url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "tipo", "Agroturismo",
                "requiere_confirmacion", true,
                "is_published", true
        );

        ResponseEntity<String> createRes = request(HttpMethod.POST, "/api/admin/turismo", createReq, token);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdJson = json(createRes);
        String attractionId = createdJson.get("id").asText();
        assertThat(createdJson.get("foto_url").asText()).isEqualTo("https://example.com/carihuairazo.jpg");
        assertThat(createdJson.get("duracion").asText()).isEqualTo("4 horas");
        assertThat(createdJson.get("dificultad").asText()).isEqualTo("Moderada");

        // Lectura pública en /api/turismo: la foto NO debe ser null
        ResponseEntity<String> pubRes = request(HttpMethod.GET, "/api/turismo", null, null);
        assertThat(pubRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pubJson = json(pubRes);

        boolean found = false;
        for (JsonNode a : pubJson) {
            if ("Ruta de los Páramos de Carihuairazo".equals(a.get("nombre").asText())) {
                found = true;
                assertThat(a.get("foto_url").asText()).isEqualTo("https://example.com/carihuairazo.jpg");
                assertThat(a.get("duracion").asText()).isEqualTo("4 horas");
                assertThat(a.get("dificultad").asText()).isEqualTo("Moderada");
                assertThat(a.get("video_url").asText()).contains("youtube.com");
                break;
            }
        }
        assertThat(found).isTrue();

        // Actualizar datos
        Map<String, Object> updateReq = Map.of(
                "nombre", "Ruta de los Páramos de Carihuairazo - Ampliada",
                "duracion", "5 horas",
                "dificultad", "Alta"
        );
        ResponseEntity<String> putRes = request(HttpMethod.PUT, "/api/admin/turismo/" + attractionId, updateReq, token);
        assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(putRes).get("duracion").asText()).isEqualTo("5 horas");
        assertThat(json(putRes).get("dificultad").asText()).isEqualTo("Alta");

        // Alternar publicación
        ResponseEntity<String> patchRes = request(HttpMethod.PATCH, "/api/admin/turismo/" + attractionId + "/publicacion", Map.of("publicado", false), token);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(patchRes).get("is_published").asBoolean()).isFalse();

        // Borrar atractivo (204)
        ResponseEntity<String> delRes = request(HttpMethod.DELETE, "/api/admin/turismo/" + attractionId, null, token);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
