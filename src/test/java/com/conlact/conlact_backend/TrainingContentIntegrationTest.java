package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingContentIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe retornar sólo secciones y contenidos publicados en el endpoint público")
    void shouldReturnOnlyPublishedSectionsAndContentsInPublicEndpoint() throws Exception {
        // 1. Crear sección publicada
        Map<String, Object> secPub = Map.of(
                "titulo", "Manejo Sanitario del Ganado",
                "descripcion", "Buenas prácticas pecuarias",
                "publicado", true
        );
        ResponseEntity<String> secRes = request(HttpMethod.POST, "/api/admin/secciones", secPub, token);
        String secId = json(secRes).get("id").asText();

        // 2. Crear un contenido publicado y un borrador en esa sección
        Map<String, Object> cPub = Map.of(
                "seccion_id", secId,
                "titulo", "Video de Ordeño Limpio",
                "tipo", "video",
                "url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "publicado", true
        );
        request(HttpMethod.POST, "/api/admin/contenidos", cPub, token);

        Map<String, Object> cDraft = Map.of(
                "seccion_id", secId,
                "titulo", "Protocolo Borrador No Publicado",
                "tipo", "texto",
                "cuerpo", "Texto de prueba que no debe verse públicamente",
                "publicado", false
        );
        request(HttpMethod.POST, "/api/admin/contenidos", cDraft, token);

        // 3. Crear otra sección en borrador
        Map<String, Object> secDraft = Map.of(
                "titulo", "Sección Borrador Oculta",
                "publicado", false
        );
        request(HttpMethod.POST, "/api/admin/secciones", secDraft, token);

        // 4. Consultar endpoint público /api/capacitacion
        ResponseEntity<String> pubRes = request(HttpMethod.GET, "/api/capacitacion", null, null);
        assertThat(pubRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pubJson = json(pubRes);

        // La sección borrador NO debe aparecer
        for (JsonNode s : pubJson) {
            assertThat(s.get("titulo").asText()).isNotEqualTo("Sección Borrador Oculta");
            if (s.get("id").asText().equals(secId)) {
                // En la sección publicada sólo debe aparecer el contenido publicado
                JsonNode contenidos = s.get("contenidos");
                assertThat(contenidos.size()).isEqualTo(1);
                assertThat(contenidos.get(0).get("titulo").asText()).isEqualTo("Video de Ordeño Limpio");
            }
        }
    }

    @Test
    @DisplayName("Debe permitir al administrador reordenar atómicamente secciones con mover arriba/abajo")
    void shouldAtomicallyReorderSections() throws Exception {
        ResponseEntity<String> res1 = request(HttpMethod.POST, "/api/admin/secciones", Map.of("titulo", "Sección Primera", "publicado", true), token);
        ResponseEntity<String> res2 = request(HttpMethod.POST, "/api/admin/secciones", Map.of("titulo", "Sección Segunda", "publicado", true), token);

        String id1 = json(res1).get("id").asText();
        String id2 = json(res2).get("id").asText();

        // Mover sección segunda hacia arriba
        ResponseEntity<String> moveRes = request(HttpMethod.PATCH, "/api/admin/secciones/" + id2, Map.of("mover", "arriba"), token);
        assertThat(moveRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verificar el nuevo orden
        ResponseEntity<String> listRes = request(HttpMethod.GET, "/api/admin/secciones", null, token);
        JsonNode listJson = json(listRes);

        int posId2 = -1;
        int posId1 = -1;
        for (int i = 0; i < listJson.size(); i++) {
            if (listJson.get(i).get("id").asText().equals(id2)) posId2 = i;
            if (listJson.get(i).get("id").asText().equals(id1)) posId1 = i;
        }

        assertThat(posId2).isLessThan(posId1);
    }

    @Test
    @DisplayName("Debe eliminar en cascada contenidos al borrar una sección")
    void shouldCascadeDeleteContentsWhenSectionDeleted() throws Exception {
        ResponseEntity<String> secRes = request(HttpMethod.POST, "/api/admin/secciones", Map.of("titulo", "Sección a Eliminar", "publicado", true), token);
        String secId = json(secRes).get("id").asText();

        ResponseEntity<String> cRes = request(HttpMethod.POST, "/api/admin/contenidos", Map.of(
                "seccion_id", secId,
                "titulo", "Contenido Efímero",
                "tipo", "texto",
                "cuerpo", "Contenido que debe ser borrado en cascada",
                "publicado", true
        ), token);
        String cId = json(cRes).get("id").asText();

        // Borrar sección (204 No Content)
        ResponseEntity<String> delRes = request(HttpMethod.DELETE, "/api/admin/secciones/" + secId, null, token);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Intentar parchear el contenido borrado debe dar 404
        ResponseEntity<String> patchRes = request(HttpMethod.PATCH, "/api/admin/contenidos/" + cId, Map.of("titulo", "Modificación Imposible"), token);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Debe rechazar contenido si falta cuerpo para tipo texto o URL para tipo enlace/video")
    void shouldRejectContentWithoutRequiredBodyOrUrl() throws Exception {
        ResponseEntity<String> secRes = request(HttpMethod.POST, "/api/admin/secciones", Map.of("titulo", "Sección Validaciones"), token);
        String secId = json(secRes).get("id").asText();

        // Texto sin cuerpo
        Map<String, Object> invalidText = Map.of(
                "seccion_id", secId,
                "titulo", "Texto Sin Cuerpo",
                "tipo", "texto"
        );
        ResponseEntity<String> res1 = request(HttpMethod.POST, "/api/admin/contenidos", invalidText, token);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Video sin URL
        Map<String, Object> invalidVideo = Map.of(
                "seccion_id", secId,
                "titulo", "Video Sin URL",
                "tipo", "video"
        );
        ResponseEntity<String> res2 = request(HttpMethod.POST, "/api/admin/contenidos", invalidVideo, token);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
