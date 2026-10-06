package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestimonialIntegrationTest extends AdminApiIntegrationSupport {
    private Map<String, Object> content() {
        return Map.of("autor_nombre", "Mercedes Caisabanda", "autor_tipo", "Maestra Quesera", "frase", "Conservamos nuestra tradición.");
    }

    @Test
    @DisplayName("BE-15: Ambos aliases públicos excluyen todas las combinaciones sin autorización o publicación")
    void shouldFilterPublicTestimonialsAndPreserveContract() throws Exception {
        UUID visible = UUID.randomUUID();
        UUID unauthorized = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        for (var row : new Object[][]{{visible, true, true}, {unauthorized, false, true}, {hidden, true, false}, {draft, false, false}}) {
            jdbc.update("INSERT INTO testimonials(id, author_name, quote, is_authorized, is_published, is_approved) VALUES (?, 'Productora', 'Frase autorizada', ?, ?, true)", row);
        }
        for (String path : new String[]{"/api/testimonios", "/api/testimonials"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = json(response);
            assertThat(body.toString()).contains(visible.toString()).doesNotContain(unauthorized.toString(), hidden.toString(), draft.toString());
            for (var item : body) {
                assertThat(item.has("autor_nombre")).isTrue();
                assertThat(item.has("frase")).isTrue();
                assertThat(item.has("fecha")).isTrue();
                assertThat(item.has("is_authorized")).isFalse();
                assertThat(item.has("is_published")).isFalse();
            }
        }
    }

    @Test
    @DisplayName("BE-15: Ciclo administrativo completo con aliases, autorización, publicación idempotente y eliminación")
    void shouldCompleteAdminLifecycle() throws Exception {
        var created = request(HttpMethod.POST, "/api/admin/testimonios", content(), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json(created).path("id").asText();
        assertThat(json(created).path("is_published").asBoolean()).isFalse();
        assertThat(request(HttpMethod.GET, "/api/admin/testimonials/" + id, null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        var list = request(HttpMethod.GET, "/api/admin/testimonios", null, token);
        assertThat(json(list).toString()).contains(id);

        var updated = request(HttpMethod.PUT, "/api/admin/testimonials/" + id,
                Map.of("autor_nombre", "Mercedes", "frase", "Nueva frase", "is_authorized", true), token);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(updated).path("frase").asText()).isEqualTo("Nueva frase");
        var published = request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/publicar", null, token);
        assertThat(json(published).path("is_published").asBoolean()).isTrue();
        assertThat(json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString()).contains(id);
        for (int attempt = 0; attempt < 2; attempt++) {
            var hidden = request(HttpMethod.PATCH, "/api/admin/testimonials/" + id + "/publish", Map.of("is_published", false), token);
            assertThat(json(hidden).path("is_published").asBoolean()).isFalse();
        }
        assertThat(request(HttpMethod.DELETE, "/api/admin/testimonios/" + id, null, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(HttpMethod.GET, "/api/admin/testimonials/" + id, null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-15: Impide publicación sin consentimiento y retira el testimonio al revocar autorización")
    void shouldEnforceAuthorizationOnEveryWrite() throws Exception {
        var invalid = Map.of("autor_nombre", "Mercedes", "frase", "Frase", "is_published", true);
        assertThat(request(HttpMethod.POST, "/api/admin/testimonios", invalid, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String id = json(request(HttpMethod.POST, "/api/admin/testimonios", content(), token)).path("id").asText();
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/publicar", null, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        request(HttpMethod.PUT, "/api/admin/testimonios/" + id,
                Map.of("autor_nombre", "Mercedes", "frase", "Frase", "is_authorized", true, "is_published", true), token);
        var revoked = request(HttpMethod.PUT, "/api/admin/testimonios/" + id,
                Map.of("autor_nombre", "Mercedes", "frase", "Frase", "is_authorized", false), token);
        assertThat(json(revoked).path("is_published").asBoolean()).isFalse();
        assertThat(json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString()).doesNotContain(id);
    }

    @Test
    @DisplayName("BE-15: Valida textos, cuerpos JSON, UUID y estados de publicación")
    void shouldRejectInvalidRequestsWithStructuredErrors() throws Exception {
        for (Object body : new Object[]{Map.of(), Map.of("autor_nombre", " ", "frase", "Frase"),
                Map.of("autor_nombre", "Mercedes", "frase", " "),
                Map.of("autor_nombre", "Mercedes", "frase", "x".repeat(4001)), "{invalid"}) {
            var response = request(HttpMethod.POST, "/api/admin/testimonios", body, token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(json(response).path("status").asInt()).isEqualTo(400);
            assertThat(json(response).path("path").asText()).isEqualTo("/api/admin/testimonios");
        }
        assertThat(request(HttpMethod.GET, "/api/admin/testimonios/not-a-uuid", null, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String id = json(request(HttpMethod.POST, "/api/admin/testimonios", content(), token)).path("id").asText();
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/publicar", Map.of(), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.DELETE, "/api/admin/testimonios/" + UUID.randomUUID(), null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-15: Un token sin perfil activo no autoriza administración")
    void shouldRejectTokensWithoutAnActiveProfile() throws Exception {
        UUID inactive = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users(id) VALUES (?)", inactive);
        jdbc.update("INSERT INTO profiles(id, full_name, is_active) VALUES (?, 'Inactivo', false)", inactive);
        for (String invalidToken : new String[]{"invalid-token", tokenFor(UUID.randomUUID()), tokenFor(inactive)}) {
            assertThat(request(HttpMethod.POST, "/api/admin/testimonios", content(), invalidToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("Testimonios: Aprobación, destacado, archivo y restauración conservan consentimiento y contenido")
    void shouldCompleteModerationLifecycle() throws Exception {
        var created = request(HttpMethod.POST, "/api/admin/testimonios", Map.of("autor", "Cliente de Pilahuín",
                "rol", "Cliente", "comentario", "Excelente queso", "avatar", "https://example.com/avatar",
                "rating", 5, "is_authorized", true), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json(created).path("id").asText();
        assertThat(json(created).path("is_approved").asBoolean()).isFalse();
        assertThat(json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString()).doesNotContain(id);
        var approved = request(HttpMethod.PATCH, "/api/admin/testimonials/" + id + "/approve", null, token);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(approved).path("is_approved").asBoolean()).isTrue();
        var publicList = json(request(HttpMethod.GET, "/api/testimonios", null, null));
        assertThat(publicList.toString()).contains(id, "https://example.com/avatar", "Excelente queso");
        assertThat(json(request(HttpMethod.GET, "/api/testimonios?destacados=true", null, null)).toString()).doesNotContain(id);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/moderar",
                Map.of("is_featured", true), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(request(HttpMethod.GET, "/api/testimonials?destacados=true", null, null)).toString()).contains(id);
        var archived = request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/archivar", null, token);
        assertThat(json(archived).path("is_archived").asBoolean()).isTrue();
        assertThat(json(archived).path("is_published").asBoolean()).isFalse();
        assertThat(json(archived).path("is_featured").asBoolean()).isFalse();
        assertThat(json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString()).doesNotContain(id);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/aprobar", null, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonials/" + id + "/moderate",
                Map.of("is_archived", false), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/aprobar", null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/moderar",
                Map.of("is_approved", false), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString()).doesNotContain(id);
    }

    @Test
    @DisplayName("Testimonios: El filtro público excluye pendientes y archivados aunque existan flags inconsistentes")
    void shouldFilterModerationStatesInDatabase() throws Exception {
        UUID visible = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID archived = UUID.randomUUID();
        for (var row : new Object[][]{{visible, true, false}, {pending, false, false}, {archived, true, true}}) {
            jdbc.update("INSERT INTO testimonials(id, author_name, quote, is_authorized, is_published, is_approved, is_archived) VALUES (?, 'Aliado', 'Frase', true, true, ?, ?)", row);
        }
        var body = json(request(HttpMethod.GET, "/api/testimonios", null, null)).toString();
        assertThat(body).contains(visible.toString()).doesNotContain(pending.toString(), archived.toString());
    }

    @Test
    @DisplayName("Testimonios: Valida calificación, avatar, moderación y permisos en las nuevas rutas")
    void shouldValidateProfileAndModerationRequests() throws Exception {
        for (var extra : new Map[]{Map.of("calificacion", 0), Map.of("calificacion", 6), Map.of("calificacion", 1.5),
                Map.of("avatar_url", "javascript:alert(1)"), Map.of("is_authorized", true, "is_published", true, "is_approved", false)}) {
            var body = new java.util.HashMap<String, Object>(content());
            body.putAll(extra);
            assertThat(request(HttpMethod.POST, "/api/admin/testimonios", body, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        String id = json(request(HttpMethod.POST, "/api/admin/testimonios", content(), token)).path("id").asText();
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/moderar", Map.of(), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/aprobar", null, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (String action : new String[]{"aprobar", "archivar", "moderar"}) {
            assertThat(request(HttpMethod.PATCH, "/api/admin/testimonios/" + id + "/" + action, Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
