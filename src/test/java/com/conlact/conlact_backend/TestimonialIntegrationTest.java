package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestimonialIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("BE-16: Endpoints duales /api/testimonios y /api/testimonials retornan testimonios publicados de BD")
    void shouldReturnPublishedTestimonialsFromDatabase() throws Exception {
        for (String path : new String[]{"/api/testimonios", "/api/testimonials"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = json(response);
            assertThat(body.isArray()).isTrue();
            assertThat(body.size()).isGreaterThanOrEqualTo(2);

            var first = body.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("autor_nombre")).isTrue();
            assertThat(first.has("frase")).isTrue();
        }
    }

    @Test
    @DisplayName("BE-16: Ciclo de vida administrativo completo de testimonios con PostgreSQL real")
    void shouldCompleteAdminTestimonialLifecycle() throws Exception {
        // 1. Crear testimonio no publicado como admin
        Map<String, Object> createPayload = Map.of(
                "autor_nombre", "Chef Rodrigo Paz",
                "autor_tipo", "Chef Ejecutivo",
                "frase", "El queso de hoja tiene la textura perfecta para derretir.",
                "is_authorized", true,
                "is_published", false
        );

        var createRes = request(HttpMethod.POST, "/api/admin/testimonios", createPayload, token);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode createdNode = json(createRes);
        String testimonialId = createdNode.path("id").asText();
        assertThat(testimonialId).isNotBlank();
        assertThat(createdNode.path("autor_nombre").asText()).isEqualTo("Chef Rodrigo Paz");
        assertThat(createdNode.path("is_published").asBoolean()).isFalse();

        // 2. Verificar que NO aparece en el listado público
        var publicRes = request(HttpMethod.GET, "/api/testimonios", null, null);
        var publicList = json(publicRes);
        for (int i = 0; i < publicList.size(); i++) {
            assertThat(publicList.get(i).path("id").asText()).isNotEqualTo(testimonialId);
        }

        // 3. Publicar testimonio como admin
        var publishRes = request(HttpMethod.PATCH, "/api/admin/testimonios/" + testimonialId + "/publicar", null, token);
        assertThat(publishRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(publishRes).path("is_published").asBoolean()).isTrue();

        // 4. Verificar que AHORA SÍ aparece en el listado público
        var updatedPublicRes = request(HttpMethod.GET, "/api/testimonios", null, null);
        var updatedList = json(updatedPublicRes);
        boolean found = false;
        for (int i = 0; i < updatedList.size(); i++) {
            if (updatedList.get(i).path("id").asText().equals(testimonialId)) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();

        // 5. Eliminar testimonio como admin
        var deleteRes = request(HttpMethod.DELETE, "/api/admin/testimonios/" + testimonialId, null, token);
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 6. Verificar en base de datos que ya no existe
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.testimonials WHERE id = ?::uuid",
                Integer.class,
                UUID.fromString(testimonialId)
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("BE-16: Validación 400 Bad Request al crear testimonios con autor o frase en blanco")
    void shouldRejectInvalidTestimonialPayload() throws Exception {
        // Frase en blanco
        Map<String, Object> blankQuote = Map.of(
                "autor_nombre", "Usuario Test",
                "frase", "   "
        );
        var res1 = request(HttpMethod.POST, "/api/admin/testimonios", blankQuote, token);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Nombre en blanco
        Map<String, Object> blankAuthor = Map.of(
                "autor_nombre", "   ",
                "frase", "Comentario válido de prueba"
        );
        var res2 = request(HttpMethod.POST, "/api/admin/testimonios", blankAuthor, token);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
