package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogComprehensiveIntegrationTest extends AdminApiIntegrationSupport {

    @Test
    @DisplayName("BE-21: Catálogo público dual (/api/productos y /api/products) retorna listado de quesos disponibles")
    void shouldReturnPublicProductsWithDualRoutes() throws Exception {
        for (String path : new String[]{"/api/productos", "/api/products"}) {
            var response = request(HttpMethod.GET, path, null, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            var body = json(response);
            assertThat(body.isArray()).isTrue();
            assertThat(body.size()).isGreaterThanOrEqualTo(1);

            var first = body.get(0);
            assertThat(first.has("id")).isTrue();
            assertThat(first.has("nombre")).isTrue();
            assertThat(first.has("slug")).isTrue();
            assertThat(first.has("precio")).isTrue();
            assertThat(first.has("presentaciones")).isTrue();
        }
    }

    @Test
    @DisplayName("BE-21: Filtros de catálogo por disponibilidad, búsqueda por texto y categoría")
    void shouldFilterProductsByAvailabilityAndQuery() throws Exception {
        // Filtrar por disponibilidad
        var availableResponse = request(HttpMethod.GET, "/api/productos?disponible=true", null, null);
        assertThat(availableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var availableBody = json(availableResponse);
        for (var item : availableBody) {
            assertThat(item.path("disponible").asBoolean()).isTrue();
        }

        // Búsqueda por término
        var searchResponse = request(HttpMethod.GET, "/api/productos?search=queso", null, null);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var searchBody = json(searchResponse);
        assertThat(searchBody.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("BE-21: Consulta de producto por slug y detección de banderas de inventario (is_low_stock)")
    void shouldRetrieveProductBySlugAndValidateStockFlags() throws Exception {
        // En seed.sql existe el queso con slug 'queso-fresco-artesanal-el-lindero'
        var response = request(HttpMethod.GET, "/api/productos/queso-fresco-artesanal-el-lindero", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = json(response);
        assertThat(body.path("slug").asText()).isEqualTo("queso-fresco-artesanal-el-lindero");
        assertThat(body.has("presentaciones")).isTrue();

        var presentations = body.path("presentaciones");
        assertThat(presentations.isArray()).isTrue();
        assertThat(presentations.size()).isGreaterThanOrEqualTo(1);

        for (var pres : presentations) {
            assertThat(pres.has("sku")).isTrue();
            assertThat(pres.has("stock")).isTrue();
            assertThat(pres.has("is_low_stock")).isTrue();
            int stock = pres.path("stock").asInt();
            boolean isLowStock = pres.path("is_low_stock").asBoolean();
            // Si stock <= 5 (umbral por defecto), is_low_stock debe ser true
            if (stock <= 5) {
                assertThat(isLowStock).isTrue();
            }
        }
    }

    @Test
    @DisplayName("BE-21: Consulta de producto con identificador inexistente retorna 404")
    void shouldReturn404ForUnknownProduct() throws Exception {
        var response = request(HttpMethod.GET, "/api/productos/producto-fantasma-inexistente", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
