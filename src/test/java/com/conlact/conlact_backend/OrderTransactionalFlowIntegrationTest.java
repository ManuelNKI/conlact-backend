package com.conlact.conlact_backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTransactionalFlowIntegrationTest extends AdminApiIntegrationSupport {

    private static final String VARIANT_ID = "ba000000-0000-0000-0000-000000000001"; // LIN-FRE-500, stock inicial: 80
    private static final String SHIPPING_ZONE_ID = "f0000000-0000-0000-0000-000000000001"; // Ambato Urbano, fee: 1.50

    @org.springframework.beans.factory.annotation.Autowired
    private com.conlact.conlact_backend.service.OrderService orderService;

    @Test
    @DisplayName("BE-31: Flujo de compra exitoso: orden creada -> reserva de stock -> webhook payphone aprobado -> confirmación de pedido y consumo de reserva")
    void testSuccessfulOrderAndWebhookPaymentFlow() throws Exception {
        // 1. Obtener stock físico inicial de la variante
        int initialStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(initialStock).isEqualTo(80);

        // 2. Crear pedido a domicilio (5 unidades x $2.50 = $12.50 + $1.50 flete = $14.00)
        Map<String, Object> orderPayload = Map.of(
                "cliente", Map.of(
                        "nombre", "Andrés Morales",
                        "cedula_ruc", "1720000001",
                        "telefono", "0998877665",
                        "email", "andres.morales@test.com",
                        "direccion", "Calle Bolívar y Guayaquil"
                ),
                "metodo_entrega", "delivery",
                "zona_envio_id", SHIPPING_ZONE_ID,
                "direccion_entrega", "Calle Bolívar y Guayaquil 4-50",
                "metodo_pago", "payphone",
                "items", List.of(
                        Map.of("variante_id", VARIANT_ID, "cantidad", 5)
                )
        );

        var createOrderResponse = request(HttpMethod.POST, "/api/pedidos", orderPayload, null);
        assertThat(createOrderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode orderNode = json(createOrderResponse);
        String orderId = orderNode.path("id").asText();
        assertThat(orderId).isNotBlank();
        assertThat(orderNode.path("subtotal").asDouble()).isEqualTo(12.50);
        assertThat(orderNode.path("flete").asDouble()).isEqualTo(1.50);
        assertThat(orderNode.path("total").asDouble()).isEqualTo(14.00);

        // 3. Verificar que el stock físico se decrementó INMEDIATAMENTE como reserva activa
        int reservedStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(reservedStock).isEqualTo(75); // 80 - 5 = 75

        String reservationStatus = jdbc.queryForObject(
                "SELECT status FROM public.inventory_reservations WHERE order_id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(reservationStatus).isEqualTo("active");

        // 4. Simular callback de Webhook PayPhone exitoso (status: Approved, statusCode: 3)
        Map<String, Object> webhookPayload = Map.of(
                "clientTransactionId", orderId,
                "transactionId", "TX-PAYPHONE-998877",
                "status", "Approved",
                "statusCode", 3
        );

        var webhookResponse = request(HttpMethod.POST, "/api/webhooks/payphone", webhookPayload, null);
        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode webhookResult = json(webhookResponse);
        assertThat(webhookResult.path("success").asBoolean()).isTrue();
        assertThat(webhookResult.path("order_status").asText()).isEqualTo("paid");
        assertThat(webhookResult.path("payment_status").asText()).isEqualTo("approved");

        // 5. Certificar estado final de la base de datos
        String finalOrderStatus = jdbc.queryForObject(
                "SELECT status FROM public.orders WHERE id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(finalOrderStatus).isEqualTo("paid");

        String finalPaymentStatus = jdbc.queryForObject(
                "SELECT payment_status FROM public.orders WHERE id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(finalPaymentStatus).isEqualTo("approved");

        String finalReservationStatus = jdbc.queryForObject(
                "SELECT status FROM public.inventory_reservations WHERE order_id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(finalReservationStatus).isEqualTo("consumed");

        // El stock permanece en 75 (no hay sobre-descuento ni fuga de stock)
        int finalStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(finalStock).isEqualTo(75);
    }

    @Test
    @DisplayName("BE-31: Flujo con fallo de pasarela: orden creada -> reserva de stock -> webhook payphone rechazado -> reversión automática de inventario a stock disponible")
    void testFailedWebhookTriggersStockRestitution() throws Exception {
        // 1. Obtener stock inicial
        int initialStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );

        // 2. Crear pedido de 10 unidades
        Map<String, Object> orderPayload = Map.of(
                "cliente", Map.of(
                        "nombre", "Beatriz Nuñez",
                        "cedula_ruc", "1800000002",
                        "telefono", "0981234567",
                        "email", "beatriz@test.com"
                ),
                "metodo_entrega", "pickup",
                "metodo_pago", "payphone",
                "items", List.of(
                        Map.of("variante_id", VARIANT_ID, "cantidad", 10)
                )
        );

        var createOrderResponse = request(HttpMethod.POST, "/api/pedidos", orderPayload, null);
        assertThat(createOrderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orderId = json(createOrderResponse).path("id").asText();
        assertThat(orderId).isNotBlank();

        // 3. Certificar que 10 unidades fueron reservadas temporalmente
        int stockDuringHold = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(stockDuringHold).isEqualTo(initialStock - 10);

        // 4. Simular callback de Webhook PayPhone fallido (status: Rejected, statusCode: 2)
        Map<String, Object> failedWebhookPayload = Map.of(
                "clientTransactionId", orderId,
                "transactionId", "TX-FAIL-112233",
                "status", "Rejected",
                "statusCode", 2,
                "message", "Tarjeta de crédito declinada por fondos insuficientes"
        );

        var webhookResponse = request(HttpMethod.POST, "/api/webhooks/payphone", failedWebhookPayload, null);
        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode webhookResult = json(webhookResponse);
        assertThat(webhookResult.path("success").asBoolean()).isTrue();
        assertThat(webhookResult.path("order_status").asText()).isEqualTo("cancelled");
        assertThat(webhookResult.path("payment_status").asText()).isEqualTo("rejected");

        // 5. Certificar REVERSIÓN TOTAL de stock en la base de datos
        int restoredStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(restoredStock).isEqualTo(initialStock); // El stock se restituyó completamente

        String releasedReservation = jdbc.queryForObject(
                "SELECT status FROM public.inventory_reservations WHERE order_id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(releasedReservation).isEqualTo("released");
    }

    @Test
    @DisplayName("BE-31: Validación de payloads mal formados en endpoint de webhooks")
    void testWebhookPayloadValidation() throws Exception {
        // Payload sin orden
        var res1 = request(HttpMethod.POST, "/api/webhooks/payphone", Map.of("status", "Approved"), null);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Payload con ID de orden inexistente
        var res2 = request(HttpMethod.POST, "/api/webhooks/payphone", Map.of(
                "clientTransactionId", UUID.randomUUID().toString(),
                "status", "Approved"
        ), null);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("BE-30 / BE-31: Expiración de reservas temporales: reversión automática de stock ante falta de pago")
    void testExpiredReservationStockReversion() throws Exception {
        int initialStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );

        // Crear pedido de 6 unidades
        Map<String, Object> orderPayload = Map.of(
                "cliente", Map.of(
                        "nombre", "Cliente Temporal",
                        "cedula_ruc", "0999999999",
                        "telefono", "0991234567"
                ),
                "metodo_entrega", "pickup",
                "metodo_pago", "payphone",
                "items", List.of(Map.of("variante_id", VARIANT_ID, "cantidad", 6))
        );

        var response = request(HttpMethod.POST, "/api/pedidos", orderPayload, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orderId = json(response).path("id").asText();

        // Stock físico descontado a (initialStock - 6)
        int reservedStock = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(reservedStock).isEqualTo(initialStock - 6);

        // Simular paso del tiempo: expirar la reserva en BD (hace 10 minutos)
        jdbc.update(
                "UPDATE public.inventory_reservations SET expires_at = NOW() - INTERVAL '10 minutes' WHERE order_id = ?::uuid",
                UUID.fromString(orderId)
        );

        // Disparar proceso de expiración
        int releasedCount = orderService.releaseExpiredReservations();
        assertThat(releasedCount).isGreaterThanOrEqualTo(1);

        // Validar que el stock físico volvió exactamente al valor inicial
        int stockAfterExpiration = jdbc.queryForObject(
                "SELECT stock FROM public.product_variants WHERE id = ?::uuid",
                Integer.class,
                VARIANT_ID
        );
        assertThat(stockAfterExpiration).isEqualTo(initialStock);

        // Validar que la orden pasó a cancelada y la reserva a released
        String orderStatus = jdbc.queryForObject(
                "SELECT status FROM public.orders WHERE id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(orderStatus).isEqualTo("cancelled");

        String reservationStatus = jdbc.queryForObject(
                "SELECT status FROM public.inventory_reservations WHERE order_id = ?::uuid",
                String.class,
                orderId
        );
        assertThat(reservationStatus).isEqualTo("released");
    }

    @Test
    @DisplayName("BE-30 / BE-31: Creación de pedido con método de pago transferencia bancaria")
    void testBankTransferOrderCreation() throws Exception {
        Map<String, Object> orderPayload = Map.of(
                "cliente", Map.of(
                        "nombre", "Empresa Láctea Asociada",
                        "cedula_ruc", "1790011223001",
                        "telefono", "022334455",
                        "email", "pagos@empresalactea.ec"
                ),
                "metodo_entrega", "pickup",
                "metodo_pago", "transferencia",
                "items", List.of(Map.of("variante_id", VARIANT_ID, "cantidad", 2))
        );

        var response = request(HttpMethod.POST, "/api/pedidos", orderPayload, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode json = json(response);
        assertThat(json.path("metodo_pago").asText()).isEqualTo("transferencia");
        assertThat(json.path("estado").asText()).isEqualTo("pending");
        assertThat(json.path("payphone_url").isNull() || json.path("payphone_url").asText().isBlank()).isTrue();
    }
}

