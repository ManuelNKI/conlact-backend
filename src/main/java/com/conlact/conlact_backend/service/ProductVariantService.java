package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.variant.ProductVariantCreateRequest;
import com.conlact.conlact_backend.dto.variant.ProductVariantResponse;
import com.conlact.conlact_backend.dto.variant.ProductVariantUpdateRequest;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductVariantResponse getVariantById(UUID id) {
        ProductVariant variant = findVariantOrThrow(id);
        return ProductVariantResponse.fromEntity(variant);
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getVariantBySku(String sku) {
        ProductVariant variant = productVariantRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada con SKU: " + sku));
        return ProductVariantResponse.fromEntity(variant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariantsByProductId(UUID productId, boolean onlyActive) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + productId);
        }

        List<ProductVariant> variants = onlyActive
                ? productVariantRepository.findByProductIdAndIsActiveTrue(productId)
                : productVariantRepository.findByProductId(productId);

        return variants.stream()
                .map(ProductVariantResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ProductVariantResponse createVariant(UUID productId, ProductVariantCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

        String sanitizedSku = request.getSku().trim().toUpperCase();
        if (productVariantRepository.existsBySkuIgnoreCase(sanitizedSku)) {
            throw new ConflictException("Ya existe una variante con el SKU: " + sanitizedSku);
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sanitizedSku)
                .presentationName(request.getPresentationName().trim())
                .weightGrams(request.getWeightGrams())
                .price(request.getPrice())
                .stock(request.getStock())
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 5)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .version(0L)
                .build();

        ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);
        log.info("Variante creada con ID: {}, SKU: {}, Producto: {}",
                savedVariant.getId(), savedVariant.getSku(), productId);

        return ProductVariantResponse.fromEntity(savedVariant);
    }

    @Transactional
    public ProductVariantResponse updateVariant(UUID variantId, ProductVariantUpdateRequest request) {
        ProductVariant variant = findVariantOrThrow(variantId);

        if (request.getSku() != null && !request.getSku().isBlank()) {
            String sanitizedSku = request.getSku().trim().toUpperCase();
            if (productVariantRepository.existsBySkuIgnoreCaseAndIdNot(sanitizedSku, variantId)) {
                throw new ConflictException("Ya existe otra variante registrada con el SKU: " + sanitizedSku);
            }
            variant.setSku(sanitizedSku);
        }

        if (request.getPresentationName() != null && !request.getPresentationName().isBlank()) {
            variant.setPresentationName(request.getPresentationName().trim());
        }

        if (request.getWeightGrams() != null) {
            variant.setWeightGrams(request.getWeightGrams());
        }

        if (request.getPrice() != null) {
            variant.setPrice(request.getPrice());
        }

        if (request.getLowStockThreshold() != null) {
            variant.setLowStockThreshold(request.getLowStockThreshold());
        }

        if (request.getIsActive() != null) {
            variant.setIsActive(request.getIsActive());
        }

        ProductVariant updatedVariant = productVariantRepository.saveAndFlush(variant);
        log.info("Variante actualizada con ID: {}, SKU: {}", updatedVariant.getId(), updatedVariant.getSku());

        return ProductVariantResponse.fromEntity(updatedVariant);
    }

    /**
     * Deduce stock del inventario central con control de concurrencia optimista (@Version).
     */
    @Transactional
    public ProductVariantResponse deductStock(UUID variantId, int quantity) {
        ProductVariant variant = findVariantOrThrow(variantId);
        variant.deductStock(quantity);

        ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);
        log.info("Stock descontado para SKU: {}. Cantidad: -{}, Stock restante: {}, Versión: {}",
                savedVariant.getSku(), quantity, savedVariant.getStock(), savedVariant.getVersion());

        return ProductVariantResponse.fromEntity(savedVariant);
    }

    /**
     * Incrementa stock en el inventario central con control de concurrencia optimista (@Version).
     */
    @Transactional
    public ProductVariantResponse addStock(UUID variantId, int quantity) {
        ProductVariant variant = findVariantOrThrow(variantId);
        variant.addStock(quantity);

        ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);
        log.info("Stock reabastecido para SKU: {}. Cantidad: +{}, Nuevo stock: {}, Versión: {}",
                savedVariant.getSku(), quantity, savedVariant.getStock(), savedVariant.getVersion());

        return ProductVariantResponse.fromEntity(savedVariant);
    }

    /**
     * Actualiza el stock a un valor absoluto con control de concurrencia optimista (@Version).
     */
    @Transactional
    public ProductVariantResponse setStock(UUID variantId, int newStock) {
        ProductVariant variant = findVariantOrThrow(variantId);
        variant.updateStock(newStock);

        ProductVariant savedVariant = productVariantRepository.saveAndFlush(variant);
        log.info("Stock ajustado para SKU: {}. Nuevo stock: {}, Versión: {}",
                savedVariant.getSku(), newStock, savedVariant.getVersion());

        return ProductVariantResponse.fromEntity(savedVariant);
    }

    /**
     * Baja lógica de la variante.
     */
    @Transactional
    public void deactivateVariant(UUID variantId) {
        ProductVariant variant = findVariantOrThrow(variantId);
        variant.setIsActive(false);
        productVariantRepository.save(variant);
        log.info("Variante desactivada con ID: {}", variantId);
    }

    private ProductVariant findVariantOrThrow(UUID id) {
        return productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variante no encontrada con ID: " + id));
    }
}
