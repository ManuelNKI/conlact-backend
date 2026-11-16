package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class AssociationExtendedFieldsIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("Debe persistir y retornar campos extendidos de asociación (parroquia, miembros, altitud, horario, campos_ficha)")
    void shouldPersistAndReturnExtendedFields() throws Exception {
        Map<String, Object> createReq = Map.ofEntries(
                entry("nombre", "Asociación Nueva Esperanza de Pilahuín"),
                entry("descripcion_corta", "Productores lecheros del sector occidental"),
                entry("historia", "Fundada por comuneros dedicados al acopio cooperativo."),
                entry("ubicacion", "Pilahuín Centro"),
                entry("parroquia", "Pilahuín"),
                entry("miembros", 42),
                entry("altitud_msnm", 3450),
                entry("numero_familias", 42),
                entry("horario_atencion", "Lunes a Viernes 06:00 - 15:00"),
                entry("registro_bpm", "BPM-CONLAC-004"),
                entry("campos_ficha", List.of(
                        Map.of("label", "Capacidad Diaria", "value", "1500 Litros de Leche"),
                        Map.of("label", "Raza Predominante", "value", "Holstein Friesian y Jersey")
                )),
                entry("is_published", true)
        );

        ResponseEntity<String> createRes = request(HttpMethod.POST, "/api/admin/asociaciones", createReq, token);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createJson = json(createRes);
        String assocId = createJson.get("id").asText();
        String slug = createJson.get("slug").asText();

        assertThat(createJson.get("parroquia").asText()).isEqualTo("Pilahuín");
        assertThat(createJson.get("miembros").asInt()).isEqualTo(42);
        assertThat(createJson.get("altitud_msnm").asInt()).isEqualTo(3450);
        assertThat(createJson.get("horario_atencion").asText()).contains("06:00 - 15:00");
        assertThat(createJson.get("registro_bpm").asText()).isEqualTo("BPM-CONLAC-004");
        assertThat(createJson.get("campos_ficha").size()).isEqualTo(2);

        // Lectura pública por slug
        ResponseEntity<String> pubRes = request(HttpMethod.GET, "/api/asociaciones/" + slug, null, null);
        assertThat(pubRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pubJson = json(pubRes);
        assertThat(pubJson.get("parroquia").asText()).isEqualTo("Pilahuín");
        assertThat(pubJson.get("miembros").asInt()).isEqualTo(42);
        assertThat(pubJson.get("campos_ficha").size()).isEqualTo(2);

        // Actualizar slug y verificar que los campos extendidos se preserven
        Map<String, Object> updateReq = Map.of(
                "nombre", "Asociación Nueva Esperanza de Pilahuín",
                "slug", "nueva-esperanza-modificada",
                "parroquia", "Pilahuín Alto",
                "miembros", 45
        );
        ResponseEntity<String> putRes = request(HttpMethod.PUT, "/api/admin/asociaciones/" + assocId, updateReq, token);
        assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode putJson = json(putRes);
        assertThat(putJson.get("slug").asText()).isEqualTo("nueva-esperanza-modificada");
        assertThat(putJson.get("parroquia").asText()).isEqualTo("Pilahuín Alto");
        assertThat(putJson.get("miembros").asInt()).isEqualTo(45);
        // Debe conservar campos_ficha anteriores
        assertThat(putJson.get("campos_ficha").size()).isEqualTo(2);
    }
}
