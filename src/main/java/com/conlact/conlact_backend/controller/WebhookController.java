package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.entity.Order;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.service.OrderService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/webhooks", "/api/webhook"})
@RequiredArgsConstructor
public class WebhookController {

    private final OrderService orderService;

    public record WebhookPayload(
            @JsonProperty("clientTransactionId") String clientTransactionId,
            @JsonProperty("order_id") String orderId,
            @JsonProperty("transactionId") String transactionId,
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("statusCode") Integer statusCode,
            @JsonProperty("message") String message
    ) {}

    @PostMapping({"/payphone", "/payment"})
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(@RequestBody(required = false) Map<String, Object> rawPayload) {
        log.info("Webhook de pago recibido: {}", rawPayload);

        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new BadRequestException("Cuerpo de webhook vacío o inválido");
        }

        // Extraer identificador de orden (soporta order_id o clientTransactionId)
        String orderIdStr = (String) rawPayload.getOrDefault("order_id", rawPayload.get("clientTransactionId"));
        if (orderIdStr == null || orderIdStr.isBlank()) {
            throw new BadRequestException("El payload del webhook debe incluir 'order_id' o 'clientTransactionId'");
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Identificador de orden inválido: " + orderIdStr);
        }

        // Evaluar estado (status = Approved / Success o statusCode = 3)
        String status = (String) rawPayload.get("status");
        Object statusCodeObj = rawPayload.get("statusCode");
        Integer statusCode = statusCodeObj instanceof Number ? ((Number) statusCodeObj).intValue() : null;

        boolean isApproved = "Approved".equalsIgnoreCase(status)
                || "Success".equalsIgnoreCase(status)
                || "Aprobado".equalsIgnoreCase(status)
                || Integer.valueOf(3).equals(statusCode);

        String transactionId = (String) rawPayload.getOrDefault("transactionId", rawPayload.get("id"));
        if (transactionId == null) {
            transactionId = "TX-" + System.currentTimeMillis();
        }

        Order processedOrder;
        if (isApproved) {
            processedOrder = orderService.confirmOrderPayment(orderId, transactionId, rawPayload);
        } else {
            String reason = (String) rawPayload.getOrDefault("message", "Rechazado o cancelado en pasarela de pagos");
            processedOrder = orderService.cancelAndRevertOrder(orderId, reason);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "order_id", processedOrder.getId().toString(),
                "order_number", processedOrder.getOrderNumber() != null ? processedOrder.getOrderNumber() : 0,
                "order_status", processedOrder.getStatus().name(),
                "payment_status", processedOrder.getPaymentStatus().name()
        ));
    }
}
