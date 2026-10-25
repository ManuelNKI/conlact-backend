package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateResponse;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/pedidos", "/api/admin/orders"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<OrderCreateResponse>> getOrders(
            @RequestParam(name = "estado", required = false) String status) {
        OrderStatus orderStatus = (status != null && !status.isBlank())
                ? OrderStatusUpdateRequest.parseStatus(status)
                : null;
        return ResponseEntity.ok(orderService.getAllOrders(orderStatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderCreateResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PatchMapping({"/{id}/estado", "/{id}/status"})
    public ResponseEntity<OrderStatusUpdateResponse> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        OrderStatusUpdateResponse response = orderService.updateOrderStatus(id, request, principal);
        return ResponseEntity.ok(response);
    }
}
