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
        return ResponseEntity.status(410).body(Map.of("error", "Las pasarelas de pago están deshabilitadas. La transferencia se verifica en administración."));
    }
}
