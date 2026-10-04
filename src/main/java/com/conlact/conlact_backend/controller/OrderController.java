package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/pedidos", "/api/orders"})
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request) {

        OrderCreateResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/track/{orderNumber}")
    public ResponseEntity<OrderCreateResponse> trackOrder(
            @PathVariable Long orderNumber) {

        OrderCreateResponse response = orderService.getOrderByOrderNumber(orderNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderCreateResponse> getOrderById(
            @PathVariable UUID id) {

        OrderCreateResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }
}
