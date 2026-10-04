package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.product.ProductPublicResponse;
import com.conlact.conlact_backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/productos", "/api/products"})
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductPublicResponse>> getProducts(
            @RequestParam(name = "asociacion", required = false) String association,
            @RequestParam(name = "categoria", required = false) String category,
            @RequestParam(name = "destacado", required = false) Boolean featured,
            @RequestParam(name = "disponible", required = false) Boolean available,
            @RequestParam(name = "search", required = false) String search) {

        List<ProductPublicResponse> products = productService.getPublishedProducts(
                association, category, featured, available, search);

        return ResponseEntity.ok(products);
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<ProductPublicResponse> getProductByIdOrSlug(
            @PathVariable String identifier) {

        ProductPublicResponse product = productService.getPublishedProductByIdOrSlug(identifier);
        return ResponseEntity.ok(product);
    }
}
