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

class PageContentIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe permitir lectura pública de página con fallback por defecto si no existe")
    void shouldAllowReadingPublicPageWithDefaultFallback() throws Exception {
        ResponseEntity<String> response = request(HttpMethod.GET, "/api/paginas/home", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = json(response);
        assertThat(json.get("slug").asText()).isEqualTo("home");
        assertThat(json.get("titulo").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("Debe rechazar guardado administrativo de página sin autenticación")
    void shouldRejectUnauthorizedPageModification() throws Exception {
        Map<String, Object> body = Map.of(
                "titulo", "Nuevo Título Portada",
                "contenido", Map.of("hero_accent", "Quesos del Páramo")
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/admin/paginas/home", body, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Debe permitir al administrador guardar y recuperar configuración de página")
    void shouldAllowAdminToSaveAndRetrievePage() throws Exception {
        Map<String, Object> body = Map.of(
                "eyebrow", "Edición Especial",
                "titulo", "Consorcio de Lácteos Tungurahua - Actualizado",
                "subtitulo", "Productos agroecológicos de altura",
                "contenido", Map.of("hero_accent", "Quesos Frescos y Curados"),
                "version", 0L
        );

        ResponseEntity<String> postRes = request(HttpMethod.POST, "/api/admin/paginas/home", body, token);
        assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode postJson = json(postRes);
        assertThat(postJson.get("ok").asBoolean()).isTrue();
        assertThat(postJson.get("data").get("titulo").asText()).isEqualTo("Consorcio de Lácteos Tungurahua - Actualizado");

        // Lectura pública inmediata
        ResponseEntity<String> getRes = request(HttpMethod.GET, "/api/paginas/home", null, null);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode getJson = json(getRes);
        assertThat(getJson.get("titulo").asText()).isEqualTo("Consorcio de Lácteos Tungurahua - Actualizado");
        assertThat(getJson.get("eyebrow").asText()).isEqualTo("Edición Especial");
    }

    @Test
    @DisplayName("Debe rechazar slug no permitido en allowlist")
    void shouldRejectDisallowedSlug() throws Exception {
        Map<String, Object> body = Map.of(
                "titulo", "Página Hack",
                "contenido", Map.of()
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/admin/paginas/slug-arbitrario-no-valido", body, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Debe rechazar asoc-{slug} si la asociación no existe")
    void shouldRejectNonExistentAssociationSlug() throws Exception {
        ResponseEntity<String> response = request(HttpMethod.GET, "/api/paginas/asoc-asociacion-inexistente-xyz", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Debe permitir asoc-{slug} cuando la asociación existe")
    void shouldAllowExistingAssociationSlug() throws Exception {
        // En seed.sql existe la asociación con slug 'asociacion-el-lindero'
        ResponseEntity<String> response = request(HttpMethod.GET, "/api/paginas/asoc-asociacion-el-lindero", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = json(response);
        assertThat(json.get("slug").asText()).isEqualTo("asoc-asociacion-el-lindero");
    }

    @Test
    @DisplayName("Debe detectar conflicto de versión optimista si la versión no coincide")
    void shouldDetectVersionConcurrencyConflict() throws Exception {
        // 1. Guardar primera versión (crea con version 1L)
        Map<String, Object> first = Map.of(
                "titulo", "Primera Versión",
                "contenido", Map.of()
        );
        ResponseEntity<String> res1 = request(HttpMethod.POST, "/api/admin/paginas/nosotros", first, token);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(res1).get("data").get("version").asLong()).isEqualTo(1L);

        // 2. Actualizar con versión 1L válida (avanza a 2L)
        Map<String, Object> second = Map.of(
                "titulo", "Segunda Versión Actualizada",
                "contenido", Map.of(),
                "version", 1L
        );
        ResponseEntity<String> res2 = request(HttpMethod.POST, "/api/admin/paginas/nosotros", second, token);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(res2).get("data").get("version").asLong()).isEqualTo(2L);

        // 3. Intentar guardar con versión antigua 1L cuando la actual es 2L
        Map<String, Object> conflict = Map.of(
                "titulo", "Tercera Versión Desactualizada",
                "contenido", Map.of(),
                "version", 1L
        );
        ResponseEntity<String> conflictRes = request(HttpMethod.POST, "/api/admin/paginas/nosotros", conflict, token);
        assertThat(conflictRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Debe sincronizar atómicamente testimonios compartidos cuando se guardan en la portada")
    void shouldAtomicallySyncTestimonialsOnHomePageSave() throws Exception {
        Map<String, Object> body = Map.of(
                "titulo", "Portada con Testimonios",
                "contenido", Map.of(),
                "testimonios", List.of(
                        Map.of(
                                "name", "Chef Carlos Vega",
                                "quote", "El queso de hoja es excepcional para nuestros platos andinos.",
                                "role", "Chef Ejecutivo",
                                "rating", 5
                        )
                )
        );

        ResponseEntity<String> response = request(HttpMethod.POST, "/api/admin/paginas/home", body, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verificar que el testimonio esté en /api/testimonios
        ResponseEntity<String> testRes = request(HttpMethod.GET, "/api/testimonios", null, null);
        assertThat(testRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode testJson = json(testRes);
        assertThat(testJson.isArray()).isTrue();

        boolean found = false;
        for (JsonNode t : testJson) {
            if ("Chef Carlos Vega".equals(t.get("autor_nombre").asText())) {
                found = true;
                assertThat(t.get("frase").asText()).contains("queso de hoja es excepcional");
                assertThat(t.get("autor_tipo").asText()).isEqualTo("Chef Ejecutivo");
                break;
            }
        }
        assertThat(found).isTrue();
    }
}
