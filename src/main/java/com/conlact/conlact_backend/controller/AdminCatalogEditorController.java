package com.conlact.conlact_backend.controller;
import com.conlact.conlact_backend.dto.product.*;
import com.conlact.conlact_backend.dto.product.image.*;
import com.conlact.conlact_backend.dto.variant.*;
import com.conlact.conlact_backend.service.*;
import com.conlact.conlact_backend.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/catalogo")
public class AdminCatalogEditorController {
    private final AdminProductService products;
    private final jakarta.persistence.EntityManager entityManager;
    private final ContentMediaService media;
    private final ProductVariantService variants;
    private final AdminInventoryService inventory;
    private final ProductImageService images;
    public record VariantEdit(UUID id, @NotBlank String sku, @NotBlank String presentation_name,
        @Min(1) Integer weight_grams, @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) java.math.BigDecimal price,
        @NotNull @Min(0) Integer stock, Long version, Boolean is_active) {}
    public record EditRequest(UUID id, @NotNull @Valid AdminProductRequest producto,
        @NotEmpty List<@Valid VariantEdit> variantes, @Size(max=7000000) String foto,
        Set<@NotNull UUID> eliminadas) {}
    @PostMapping @Transactional
    public AdminProductResponse save(@Valid @RequestBody EditRequest data, @AuthenticationPrincipal UserPrincipal principal) {
        var product = data.id() == null ? products.createProduct(data.producto()) : products.updateProduct(data.id(),data.producto());
        if (data.eliminadas() != null) {
            for (UUID id : data.eliminadas()) {
                if (data.variantes().stream().anyMatch(v -> id.equals(v.id()))) {
                    throw new com.conlact.conlact_backend.exception.BadRequestException("Una presentación no puede guardarse y eliminarse a la vez");
                }
                if (!product.id().equals(variants.getVariantById(id).getProductId())) {
                    throw new com.conlact.conlact_backend.exception.BadRequestException("La presentación no pertenece al producto");
                }
                // Keep references from historical orders; remove from purchasable presentations.
                variants.deactivateVariant(id);
            }
        }
        for(var v : data.variantes()) {
            if(v.id() == null) {
                variants.createVariant(product.id(), ProductVariantCreateRequest.builder().sku(v.sku()).presentationName(v.presentation_name())
                    .weightGrams(v.weight_grams()).price(v.price()).stock(v.stock()).isActive(v.is_active()==null || v.is_active()).build());
            } else {
                var existing = variants.getVariantById(v.id());
                if(!product.id().equals(existing.getProductId())) throw new com.conlact.conlact_backend.exception.BadRequestException("La variante no pertenece al producto");
                inventory.setStock(v.id(), StockSetRequest.builder().stock(v.stock()).version(v.version()).reason("Edición de catálogo").build(), principal==null?null:principal.getId());
                variants.updateVariant(v.id(), ProductVariantUpdateRequest.builder().sku(v.sku()).presentationName(v.presentation_name())
                    .weightGrams(v.weight_grams()).price(v.price()).isActive(v.is_active()==null || v.is_active()).build());
            }
        }
        String photo = media.image(data.foto(),"product-images");
        if(photo!=null && !photo.isBlank()) {
            var gallery = images.getProductImages(product.id());
            var match = gallery.stream().filter(i -> photo.equals(i.storagePath()) || photo.equals(i.url())).findFirst();
            if(match.isPresent()) images.setPrimaryImage(product.id(),match.get().id());
            else images.addProductImage(product.id(),new ProductImageCreateRequest(photo,product.name(),0,true));
        }
        entityManager.flush();
        entityManager.clear();
        return products.getProductById(product.id());
    }
}
