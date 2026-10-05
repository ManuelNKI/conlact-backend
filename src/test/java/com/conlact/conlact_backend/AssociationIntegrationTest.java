package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssociationIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("BE-16: Endpoints públicos duales /api/asociaciones y /api/associations retornan lista de asociaciones activas")
    void shouldReturnActiveAssociationsWithDualRoutes() throws Exception {
        for (String path : new String[]{"/api/asociaciones", "/api/associations"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = json(response);
            assertThat(body.isArray()).isTrue();
            assertThat(body.size()).isGreaterThanOrEqualTo(1);

            // Validar campos de contrato esperados por el frontend
            var firstItem = body.get(0);
            assertThat(firstItem.has("id")).isTrue();
            assertThat(firstItem.has("nombre")).isTrue();
            assertThat(firstItem.has("slug")).isTrue();
        }
    }

    @Test
    @DisplayName("BE-16: Búsqueda de asociación por slug existente retorna 200 y detalle completo")
    void shouldFindAssociationBySlug() throws Exception {
        // En seed.sql existe la asociación con slug 'asociacion-el-lindero'
        var response = request(HttpMethod.GET, "/api/asociaciones/asociacion-el-lindero", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = json(response);
        assertThat(body.path("slug").asText()).isEqualTo("asociacion-el-lindero");
        assertThat(body.path("nombre").asText()).contains("Lindero");
    }

    @Test
    @DisplayName("BE-16: Búsqueda de asociación con slug o UUID inexistente retorna 404 Not Found")
    void shouldReturn404WhenAssociationNotFound() throws Exception {
        var response = request(HttpMethod.GET, "/api/asociaciones/slug-inexistente-xyz-999", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var responseUuid = request(HttpMethod.GET, "/api/asociaciones/" + UUID.randomUUID(), null, null);
        assertThat(responseUuid.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-16: Endpoints administrativos exigen autenticación con rol ADMIN (401 sin token)")
    void shouldRejectUnauthenticatedAdminAccess() throws Exception {
        var response = request(HttpMethod.GET, "/api/admin/asociaciones", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var postResponse = request(HttpMethod.POST, "/api/admin/asociaciones", Map.of("nombre", "Asoc Test"), null);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("BE-16: Ciclo de vida administrativo completo (creación, consulta, edición y baja lógica) con token ADMIN")
    void shouldCompleteAdminAssociationLifecycle() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        String name = "Asociación Nueva " + uniqueSuffix;

        Map<String, Object> createPayload = Map.of(
                "nombre", name,
                "descripcion_corta", "Productores de Pilahuín",
                "historia", "Asociación comunitaria de productores de altura",
                "ubicacion", "Sector El Lindero, Tungurahua",
                "sello_sanitario", "BPM-TEST-" + uniqueSuffix
        );

        // 1. Crear asociación
        var createResponse = request(HttpMethod.POST, "/api/admin/asociaciones", createPayload, token);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var createdBody = json(createResponse);
        String createdId = createdBody.path("id").asText();
        String createdSlug = createdBody.path("slug").asText();
        assertThat(createdId).isNotBlank();
        assertThat(createdSlug).isNotBlank();

        // 2. Consultar por admin
        var getAdminResponse = request(HttpMethod.GET, "/api/admin/asociaciones/" + createdId, null, token);
        assertThat(getAdminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(getAdminResponse).path("nombre").asText()).isEqualTo(name);

        // 3. Editar datos
        Map<String, Object> updatePayload = Map.of(
                "nombre", name + " Modificada",
                "descripcion_corta", "Nueva descripción actualizada"
        );
        var updateResponse = request(HttpMethod.PUT, "/api/admin/asociaciones/" + createdId, updatePayload, token);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(updateResponse).path("nombre").asText()).isEqualTo(name + " Modificada");
        assertThat(json(updateResponse).path("descripcion_corta").asText()).isEqualTo("Nueva descripción actualizada");

        // 4. Baja lógica
        var deleteResponse = request(HttpMethod.DELETE, "/api/admin/asociaciones/" + createdId, null, token);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verificar que ya no aparece en el listado público activo
        var publicList = request(HttpMethod.GET, "/api/asociaciones", null, null);
        assertThat(publicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(publicList).toString()).doesNotContain(name + " Modificada");
    }
}
