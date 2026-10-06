package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.product.AdminProductRequest;
import com.conlact.conlact_backend.dto.product.AdminProductResponse;
import com.conlact.conlact_backend.dto.variant.ProductVariantCreateRequest;
import com.conlact.conlact_backend.dto.variant.ProductVariantResponse;
import com.conlact.conlact_backend.dto.product.image.ProductImageCreateRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageResponse;
import com.conlact.conlact_backend.service.AdminProductService;
import com.conlact.conlact_backend.service.ProductImageService;
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
    private final ProductImageService productImageService;

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

    // ==========================================
    // Galería de Imágenes (BE-20)
    // ==========================================

    @GetMapping({"/{id}/imagenes", "/{id}/images", "/{id}/fotos", "/{id}/photos"})
    public ResponseEntity<List<ProductImageResponse>> getProductImages(@PathVariable UUID id) {
        return ResponseEntity.ok(productImageService.getProductImages(id));
    }

    @PostMapping({"/{id}/imagenes", "/{id}/images", "/{id}/fotos", "/{id}/photos"})
    public ResponseEntity<ProductImageResponse> addProductImage(
            @PathVariable UUID id, @Valid @RequestBody ProductImageCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productImageService.addProductImage(id, request));
    }

    @PatchMapping({"/{id}/imagenes/{imageId}/principal", "/{id}/images/{imageId}/primary", "/{id}/imagenes/{imageId}/portada"})
    public ResponseEntity<ProductImageResponse> setPrimaryImage(
            @PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.ok(productImageService.setPrimaryImage(id, imageId));
    }

    @PutMapping({"/{id}/imagenes/{imageId}/principal", "/{id}/images/{imageId}/primary", "/{id}/imagenes/{imageId}/portada"})
    public ResponseEntity<ProductImageResponse> setPrimaryImagePut(
            @PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.ok(productImageService.setPrimaryImage(id, imageId));
    }

    @PutMapping({"/{id}/imagenes/orden", "/{id}/images/reorder", "/{id}/imagenes/reorder"})
    public ResponseEntity<List<ProductImageResponse>> reorderProductImages(
            @PathVariable UUID id, @Valid @RequestBody ProductImageReorderRequest request) {
        return ResponseEntity.ok(productImageService.reorderProductImages(id, request.images()));
    }

    @DeleteMapping({"/{id}/imagenes/{imageId}", "/{id}/images/{imageId}", "/{id}/fotos/{imageId}", "/{id}/photos/{imageId}"})
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable UUID id, @PathVariable UUID imageId) {
        productImageService.deleteProductImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
