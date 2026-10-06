package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.variant.*;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.AdminInventoryService;
import com.conlact.conlact_backend.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/variantes", "/api/admin/variants"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProductVariantController {
    private final ProductVariantService productVariantService;
    private final AdminInventoryService adminInventoryService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductVariantResponse> getVariant(@PathVariable UUID id) {
        return ResponseEntity.ok(productVariantService.getVariantById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductVariantResponse> updateVariant(
            @PathVariable UUID id, @Valid @RequestBody ProductVariantUpdateRequest request) {
        return ResponseEntity.ok(productVariantService.updateVariant(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateVariant(@PathVariable UUID id) {
        productVariantService.deactivateVariant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stock/deduct")
    public ResponseEntity<ProductVariantResponse> deductStock(
            @PathVariable UUID id, @Valid @RequestBody StockOperationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminInventoryService.deductStock(id, request, actorId(principal)));
    }

    @PostMapping("/{id}/stock/replenish")
    public ResponseEntity<ProductVariantResponse> replenishStock(
            @PathVariable UUID id, @Valid @RequestBody StockOperationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminInventoryService.replenishStock(id, request, actorId(principal)));
    }

    @PostMapping("/{id}/stock/set")
    public ResponseEntity<ProductVariantResponse> setStock(
            @PathVariable UUID id, @Valid @RequestBody StockSetRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminInventoryService.setStock(id, request, actorId(principal)));
    }

    private UUID actorId(UserPrincipal principal) {
        return principal == null ? null : principal.getId();
    }
}
