package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.variant.StockOperationRequest;
import com.conlact.conlact_backend.repository.ProductVariantRepository;
import com.conlact.conlact_backend.service.AdminInventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class AdminCatalogIntegrationTest extends AdminApiIntegrationSupport {
    private static final String ASSOCIATION = "a0000000-0000-0000-0000-000000000001";
    private static final String CATEGORY = "c0000000-0000-0000-0000-000000000001";
    @Autowired private AdminInventoryService inventoryService;
    @Autowired private ProductVariantRepository variants;
    @Autowired private PlatformTransactionManager transactions;

    private Map<String, Object> productBody() {
        return new LinkedHashMap<>(Map.of("nombre", "Queso BE-19 " + UUID.randomUUID(), "asociacion_id", ASSOCIATION,
                "categoria_id", CATEGORY, "tipo_queso", "Fresco", "status", "published", "is_featured", true));
    }

    private String createProduct() throws Exception {
        var response = request(HttpMethod.POST, "/api/admin/productos", productBody(), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(response).path("created_at").isTextual()).isTrue();
        assertThat(json(response).path("updated_at").isTextual()).isTrue();
        return json(response).path("id").asText();
    }

    private String createVariant(String productId, int stock) throws Exception {
        var response = request(HttpMethod.POST, "/api/admin/products/" + productId + "/variants",
                Map.of("sku", "BE19-" + UUID.randomUUID(), "presentation_name", "Bloque 500 g", "weight_grams", 500,
                        "price", 2.50, "stock", stock, "low_stock_threshold", 5), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(response).path("created_at").isTextual()).isTrue();
        assertThat(json(response).path("updated_at").isTextual()).isTrue();
        return json(response).path("id").asText();
    }

    private int storedStock(String variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=?", Integer.class, UUID.fromString(variantId));
    }

    private long auditCount(String variantId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_name='product_variants' AND entity_id=?", Long.class, variantId);
    }

    @Test
    @DisplayName("BE-19: CRUD de productos en ambos aliases conserva variantes y permite ocultar y republicar")
    void shouldCompleteProductLifecycle() throws Exception {
        String id = createProduct();
        String variantId = createVariant(id, 10);
        var detail = json(request(HttpMethod.GET, "/api/admin/products/" + id, null, token));
        assertThat(detail.path("asociacion").asText()).isEqualTo("Asociación El Lindero");
        assertThat(detail.path("stock").asInt()).isEqualTo(10);
        assertThat(detail.path("precio").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(detail.path("peso").asText()).isEqualTo("500 g");
        assertThat(detail.path("disponible").asBoolean()).isTrue();
        assertThat(detail.path("presentaciones").get(0).path("id").asText()).isEqualTo(variantId);
        var update = productBody();
        update.put("nombre", "Nombre actualizado");
        var edited = json(request(HttpMethod.PUT, "/api/admin/productos/" + id, update, token));
        assertThat(edited.path("nombre").asText()).isEqualTo("Nombre actualizado");
        assertThat(edited.path("slug").asText()).isEqualTo(detail.path("slug").asText());
        assertThat(edited.path("presentaciones").get(0).path("id").asText()).isEqualTo(variantId);
        assertThat(json(request(HttpMethod.GET, "/api/admin/products", null, token)).toString()).contains(id);
        assertThat(request(HttpMethod.DELETE, "/api/admin/products/" + id, null, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var hidden = json(request(HttpMethod.GET, "/api/admin/productos/" + id, null, token));
        assertThat(hidden.path("status").asText()).isEqualTo("hidden");
        assertThat(hidden.path("disponible").asBoolean()).isFalse();
        assertThat(storedStock(variantId)).isEqualTo(10);
        assertThat(request(HttpMethod.PUT, "/api/admin/products/" + id, update, token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("BE-19: Referencias inexistentes, slugs duplicados, JSON y UUID inválidos producen 400/404/409")
    void shouldValidateProductsAndReferences() throws Exception {
        var body = productBody();
        body.put("slug", "be19-" + UUID.randomUUID());
        assertThat(request(HttpMethod.POST, "/api/admin/productos", body, token).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(request(HttpMethod.POST, "/api/admin/products", body, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        for (String field : new String[]{"asociacion_id", "categoria_id"}) {
            var missing = productBody();
            missing.put(field, UUID.randomUUID().toString());
            assertThat(request(HttpMethod.POST, "/api/admin/productos", missing, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        for (Object invalid : new Object[]{Map.of(), Map.of("nombre", " "), Map.of("nombre", "Queso", "status", "sold_out"),
                Map.of("nombre", "Queso", "asociacion_id", "not-a-uuid"), "{invalid"}) {
            assertThat(request(HttpMethod.POST, "/api/admin/products", invalid, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(HttpMethod.GET, "/api/admin/products/not-a-uuid", null, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.DELETE, "/api/admin/products/" + UUID.randomUUID(), null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-19: CRUD de variantes valida SKU, precio y actividad sin modificar stock por la ruta de metadatos")
    void shouldManageVariantsAndRejectDuplicateSkus() throws Exception {
        String productId = createProduct();
        String id = createVariant(productId, 10);
        var initial = json(request(HttpMethod.GET, "/api/admin/variantes/" + id, null, token));
        var duplicate = request(HttpMethod.POST, "/api/admin/productos/" + productId + "/variantes",
                Map.of("sku", initial.path("sku").asText().toLowerCase(), "presentation_name", "Otro bloque", "price", 3.25, "stock", 0), token);
        assertThat(duplicate.getStatusCode()).as(duplicate.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.POST, "/api/admin/products/" + productId + "/variants",
                Map.of("sku", "MISSING-STOCK-" + UUID.randomUUID(), "presentation_name", "Otro bloque", "price", 3.25), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var changed = request(HttpMethod.PUT, "/api/admin/variants/" + id,
                Map.of("presentation_name", "Bloque empacado", "price", 3.25, "low_stock_threshold", 12), token);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(changed).path("is_low_stock").asBoolean()).isTrue();
        assertThat(json(changed).path("version").asLong()).isGreaterThan(initial.path("version").asLong());
        assertThat(storedStock(id)).isEqualTo(10);
        assertThat(request(HttpMethod.PUT, "/api/admin/variants/" + id, Map.of("price", 3.251), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.DELETE, "/api/admin/variantes/" + id, null, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var detail = json(request(HttpMethod.GET, "/api/admin/productos/" + productId, null, token));
        assertThat(detail.path("stock").asInt()).isZero();
        assertThat(detail.path("presentaciones").size()).isEqualTo(1);
        assertThat(json(request(HttpMethod.GET, "/api/admin/products/" + productId + "/variants?onlyActive=true", null, token)).size()).isZero();
        assertThat(request(HttpMethod.PUT, "/api/admin/variants/" + id, Map.of("is_active", true), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, "/api/admin/variants/" + UUID.randomUUID(), null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(HttpMethod.GET, "/api/admin/products/" + UUID.randomUUID() + "/variants", null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-19: Descuento, reposición y ajuste son auditados con actor, motivo y versiones")
    void shouldAuditStockOperationsAndRejectStaleVersions() throws Exception {
        String id = createVariant(createProduct(), 10);
        var deducted = request(HttpMethod.POST, "/api/admin/variantes/" + id + "/stock/deduct",
                Map.of("quantity", 3, "reason", " venta_mostrador ", "version", 0), token);
        assertThat(deducted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(deducted).path("stock").asInt()).isEqualTo(7);
        long version = json(deducted).path("version").asLong();
        assertThat(version).isGreaterThan(0);
        var audit = jdbc.queryForMap("SELECT actor_user_id, old_data->>'stock' AS old_stock, new_data->>'stock' AS new_stock, new_data->>'reason' AS reason FROM audit_log WHERE entity_id=?", id);
        assertThat(audit.get("actor_user_id")).isEqualTo(ADMIN_ID);
        assertThat(audit.get("old_stock")).isEqualTo("10");
        assertThat(audit.get("new_stock")).isEqualTo("7");
        assertThat(audit.get("reason")).isEqualTo("venta_mostrador");
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/set", Map.of("stock", 100, "version", 0), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(storedStock(id)).isEqualTo(7);
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/replenish", Map.of("quantity", 50), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedStock(id)).isEqualTo(57);
        assertThat(request(HttpMethod.POST, "/api/admin/variantes/" + id + "/stock/set", Map.of("stock", 45, "reason", "toma_fisica"), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedStock(id)).isEqualTo(45);
        assertThat(auditCount(id)).isEqualTo(3);
    }

    @Test
    @DisplayName("BE-19: Stock insuficiente, cantidades inválidas y desbordamientos conservan stock y auditoría")
    void shouldRejectInvalidStockOperationsWithoutSideEffects() throws Exception {
        String id = createVariant(createProduct(), 10);
        for (Object invalid : new Object[]{Map.of(), Map.of("quantity", 0), Map.of("quantity", -1),
                Map.of("quantity", 1.5), Map.of("quantity", 2147483648L),
                Map.of("quantity", 1, "reason", "x".repeat(501)), "{invalid"}) {
            assertThat(request(HttpMethod.POST, "/api/admin/variantes/" + id + "/stock/deduct", invalid, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/deduct", Map.of("quantity", 11), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/replenish", Map.of("quantity", Integer.MAX_VALUE), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/set", Map.of("stock", -1), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(storedStock(id)).isEqualTo(10);
        assertThat(auditCount(id)).isZero();
        assertThat(request(HttpMethod.POST, "/api/admin/variants/" + id + "/stock/set", Map.of("stock", 0), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedStock(id)).isZero();
    }

    @Test
    @DisplayName("BE-19: Un fallo de auditoría revierte la actualización de inventario en la misma transacción")
    void shouldRollBackStockIfAuditCannotBeSaved() throws Exception {
        String id = createVariant(createProduct(), 10);
        var body = StockOperationRequest.builder().quantity(3).reason("Prueba de atomicidad").build();
        assertThatThrownBy(() -> inventoryService.deductStock(UUID.fromString(id), body, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(storedStock(id)).isEqualTo(10);
        assertThat(auditCount(id)).isZero();
    }

    @Test
    @DisplayName("BE-19: Dos transacciones por la última unidad producen un éxito y un conflicto sin doble descuento")
    void shouldPreserveLastUnitUnderConcurrentTransactions() throws Exception {
        String id = createVariant(createProduct(), 1);
        UUID variantId = UUID.fromString(id);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> worker = () -> {
            try {
                return new TransactionTemplate(transactions).execute(status -> {
                    variants.findById(variantId).orElseThrow();
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("No se pudo sincronizar la prueba", e);
                    }
                    inventoryService.deductStock(variantId, StockOperationRequest.builder().quantity(1).build(), ADMIN_ID);
                    return 200;
                });
            } catch (ObjectOptimisticLockingFailureException expected) {
                return 409;
            }
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(worker);
            Future<Integer> second = executor.submit(worker);
            assertThat(new Integer[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)}).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(storedStock(id)).isZero();
        assertThat(auditCount(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE-19: Ocultar un producto conserva el detalle de pedidos que ya lo referencia")
    void shouldKeepOrderHistoryWhenProductIsHidden() throws Exception {
        String productId = createProduct();
        String variantId = createVariant(productId, 3);
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id, customer_name, customer_tax_id, customer_phone, delivery_method,
                                   subtotal, total, payment_method)
                VALUES (?, 'Cliente', '1800000000', '0990000000', 'pickup', 2.50, 2.50, 'bank_transfer')
                """, orderId);
        jdbc.update("""
                INSERT INTO order_items(order_id, product_variant_id, product_name_snapshot, presentation_snapshot,
                                        sku_snapshot, unit_price, quantity)
                VALUES (?, ?, 'Queso vendido', '500 g', 'SKU-HISTORICO', 2.50, 1)
                """, orderId, UUID.fromString(variantId));
        assertThat(request(HttpMethod.DELETE, "/api/admin/productos/" + productId, null, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id=?", Integer.class, orderId)).isEqualTo(1);
        assertThat(storedStock(variantId)).isEqualTo(3);
    }
}
