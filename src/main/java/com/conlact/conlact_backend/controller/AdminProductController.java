package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.product.AdminProductRequest;
import com.conlact.conlact_backend.dto.product.AdminProductResponse;
import com.conlact.conlact_backend.dto.variant.ProductVariantCreateRequest;
import com.conlact.conlact_backend.dto.variant.ProductVariantResponse;
import com.conlact.conlact_backend.service.AdminProductService;
import com.conlact.conlact_backend.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/productos", "/api/admin/products"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {
    private final AdminProductService adminProductService;
    private final ProductVariantService productVariantService;

    @GetMapping
    public ResponseEntity<List<AdminProductResponse>> getProducts() {
        return ResponseEntity.ok(adminProductService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminProductResponse> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(adminProductService.getProductById(id));
    }

    @PostMapping
    public ResponseEntity<AdminProductResponse> createProduct(@Valid @RequestBody AdminProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminProductService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminProductResponse> updateProduct(
            @PathVariable UUID id, @Valid @RequestBody AdminProductRequest request) {
        return ResponseEntity.ok(adminProductService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hideProduct(@PathVariable UUID id) {
        adminProductService.hideProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/{id}/variantes", "/{id}/variants"})
    public ResponseEntity<List<ProductVariantResponse>> getVariants(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean onlyActive) {
        return ResponseEntity.ok(productVariantService.getVariantsByProductId(id, onlyActive));
    }

    @PostMapping({"/{id}/variantes", "/{id}/variants"})
    public ResponseEntity<ProductVariantResponse> createVariant(
            @PathVariable UUID id, @Valid @RequestBody ProductVariantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productVariantService.createVariant(id, request));
    }
}
