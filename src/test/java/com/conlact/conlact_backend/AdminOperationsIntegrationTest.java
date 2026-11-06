package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AdminOperationsIntegrationTest extends AdminApiIntegrationSupport {
    private JsonNode order(String variant) throws Exception {
        var response = request(HttpMethod.POST, "/api/pedidos", Map.of(
                "cliente", Map.of("nombre", "Prueba de operaciones", "cedula_ruc", "1720000001", "telefono", "0998877665"),
                "metodo_entrega", "pickup", "metodo_pago", "transferencia",
                "items", List.of(Map.of("variante_id", variant, "cantidad", 1))), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json(response);
    }

    private Map<String,Object> variant(int weight) {
        return new LinkedHashMap<>(Map.of("sku", "OPS-" + UUID.randomUUID(), "presentation_name", weight + " g",
                "weight_grams", weight, "price", 2.50, "stock", 10, "is_active", true));
    }

    private Map<String,Object> product() { return Map.of("nombre", "Queso operaciones " + UUID.randomUUID(), "status", "published"); }

    @Test
    void filtersOrdersAndCsvUsingPostgresEnumForEveryStatus() throws Exception {
        String orderId = order("ba000000-0000-0000-0000-000000000001").path("id").asText();
        for (String status : List.of("pending", "paid", "preparing", "shipped", "completed", "cancelled", "pendiente")) {
            var response = request(HttpMethod.GET, "/api/admin/pedidos/operacion?estado=" + status, null, token);
            assertThat(response.getStatusCode()).as(status).isEqualTo(HttpStatus.OK);
            if (status.equals("pending") || status.equals("pendiente")) assertThat(response.getBody()).contains(orderId);
            var csv = request(HttpMethod.GET, "/api/admin/pedidos/exportar-csv?estado=" + status, null, token);
            assertThat(csv.getStatusCode()).as("CSV " + status).isEqualTo(HttpStatus.OK);
        }
        assertThat(request(HttpMethod.GET, "/api/admin/pedidos/operacion?estado=pending", null, null)
                .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void removesPresentationFromSaleAndPreservesExistingOrder() throws Exception {
        var body = product();
        var created = request(HttpMethod.POST, "/api/admin/catalogo", Map.of("producto", body,
                "variantes", List.of(variant(250), variant(500))), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        var saved = json(created);
        String productId = saved.path("id").asText();
        var removed = saved.path("presentaciones").get(0);
        var kept = saved.path("presentaciones").get(1);
        String removedId = removed.path("id").asText();
        String orderId = order(removedId).path("id").asText();
        var retained = variant(500);
        retained.put("id", kept.path("id").asText()); retained.put("sku", kept.path("sku").asText());
        retained.put("version", kept.path("version").asLong());
        var response = request(HttpMethod.POST, "/api/admin/catalogo", Map.of("id", productId, "producto", body,
                "variantes", List.of(retained), "eliminadas", List.of(removedId)), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var published = json(request(HttpMethod.GET, "/api/products/" + productId, null, null));
        assertThat(published.path("presentaciones").size()).isEqualTo(1);
        assertThat(published.path("presentaciones").get(0).path("id").asText()).isEqualTo(kept.path("id").asText());
        assertThat(jdbc.queryForObject("SELECT is_active FROM product_variants WHERE id=?::uuid", Boolean.class, removedId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id=?::uuid AND product_variant_id=?::uuid",
                Integer.class, orderId, removedId)).isEqualTo(1);
    }

    @Test
    void refusesRemovingAnotherProductsPresentationAndRollsBackChanges() throws Exception {
        var created = json(request(HttpMethod.POST, "/api/admin/catalogo", Map.of("producto", product(),
                "variantes", List.of(variant(500))), token));
        String id = created.path("id").asText();
        var response = request(HttpMethod.POST, "/api/admin/catalogo", Map.of("id", id, "producto", product(),
                "variantes", List.of(variant(250)), "eliminadas", List.of("ba000000-0000-0000-0000-000000000001")), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var unchanged = json(request(HttpMethod.GET, "/api/admin/productos/" + id, null, token));
        assertThat(unchanged.path("nombre").asText()).isEqualTo(created.path("nombre").asText());
        assertThat(unchanged.path("presentaciones").size()).isEqualTo(1);
    }

    @Test
    void requiresAtLeastOneRetainedPresentation() throws Exception {
        var response = request(HttpMethod.POST, "/api/admin/catalogo", Map.of("producto", product(),
                "variantes", List.of()), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
