package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TouristAttractionIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("BE-26: Endpoints duales /api/turismo y /api/tourism retornan atractivos turísticos sembrados")
    void shouldReturnPublishedAttractions() throws Exception {
        for (String path : new String[]{"/api/turismo", "/api/tourism"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = json(response);
            assertThat(body.isArray()).isTrue();
            assertThat(body.size()).isGreaterThanOrEqualTo(2);

            var first = body.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("nombre")).isTrue();
            assertThat(first.has("tipo")).isTrue();
            assertThat(first.has("descripcion")).isTrue();
            assertThat(first.has("lat")).isTrue();
            assertThat(first.has("lng")).isTrue();
            assertThat(first.has("requiere_confirmacion")).isTrue();
        }
    }

    @Test
    @DisplayName("BE-26: Filtro de atractivos turísticos por ID de asociación")
    void shouldFilterAttractionsByAssociation() throws Exception {
        UUID assocId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        var response = request(HttpMethod.GET, "/api/turismo?asociacion_id=" + assocId, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = json(response);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);

        for (int i = 0; i < body.size(); i++) {
            assertThat(body.get(i).path("asociacion_id").asText()).isEqualTo(assocId.toString());
        }
    }

    @Test
    @DisplayName("BE-26: Consulta de atractivo turístico por ID existente")
    void shouldFindAttractionById() throws Exception {
        UUID attractionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var response = request(HttpMethod.GET, "/api/turismo/" + attractionId, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = json(response);
        assertThat(body.path("id").asText()).isEqualTo(attractionId.toString());
        assertThat(body.path("nombre").asText()).contains("Mirador y Pastizales");
        assertThat(body.path("lat").asDouble()).isEqualTo(-1.298500);
        assertThat(body.path("lng").asDouble()).isEqualTo(-78.712300);
    }

    @Test
    @DisplayName("BE-26: Consulta de atractivo turístico inexistente retorna 404 Not Found")
    void shouldReturn404ForUnknownAttraction() throws Exception {
        UUID unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        var response = request(HttpMethod.GET, "/api/turismo/" + unknownId, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
