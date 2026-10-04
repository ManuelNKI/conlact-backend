package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.product.image.ProductImageCreateRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageReorderItemRequest;
import com.conlact.conlact_backend.dto.product.image.ProductImageResponse;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductImage;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProductImageRepository;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.storage.IStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final IStorage storage;
    private final String productBucket;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            IStorage storage,
            @Value("${app.supabase.storage.product-bucket:product-images}") String productBucket) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.storage = storage;
        this.productBucket = productBucket;
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getProductImages(UUID productId) {
        findProduct(productId);
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getPublicProductImages(String identifier) {
        Product product = findPublishedProduct(identifier);
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductImageResponse addProductImage(UUID productId, ProductImageCreateRequest request) {
        Product product = findProduct(productId);
        String cleanStoragePath = request.storagePath().trim();

        if (productImageRepository.existsByProductIdAndStoragePath(productId, cleanStoragePath)) {
            throw new ConflictException("Ya existe una fotografía asociada con la ruta: " + cleanStoragePath);
        }

        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        boolean makePrimary = Boolean.TRUE.equals(request.isPrimary())
                || (request.sortOrder() != null && request.sortOrder() == 0)
                || existingImages.isEmpty();

        int targetSortOrder;
        if (makePrimary) {
            targetSortOrder = 0;
            for (ProductImage existing : existingImages) {
                existing.setSortOrder(existing.getSortOrder() + 1);
            }
            productImageRepository.saveAll(existingImages);
        } else if (request.sortOrder() != null && request.sortOrder() > 0) {
            targetSortOrder = request.sortOrder();
        } else {
            targetSortOrder = existingImages.stream()
                    .mapToInt(ProductImage::getSortOrder)
                    .max()
                    .orElse(0) + 1;
        }

        String alt = (request.altText() != null && !request.altText().isBlank()) ? request.altText().trim() : null;

        ProductImage newImage = ProductImage.builder()
                .product(product)
                .storagePath(cleanStoragePath)
                .altText(alt)
                .sortOrder(targetSortOrder)
                .build();

        product.addImage(newImage);
        ProductImage saved = productImageRepository.save(newImage);

        return toResponse(saved);
    }

    @Transactional
    public ProductImageResponse setPrimaryImage(UUID productId, UUID imageId) {
        findProduct(productId);

        ProductImage targetImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada con ID: " + imageId + " para el producto: " + productId));

        List<ProductImage> allImages = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);

        List<ProductImage> otherImages = allImages.stream()
                .filter(img -> !img.getId().equals(imageId))
                .sorted(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
                .toList();

        int nextOrder = 1;
        for (ProductImage img : otherImages) {
            img.setSortOrder(nextOrder++);
        }

        targetImage.setSortOrder(0);
        productImageRepository.saveAll(allImages);

        return toResponse(targetImage);
    }

    @Transactional
    public List<ProductImageResponse> reorderProductImages(UUID productId, List<ProductImageReorderItemRequest> items) {
        findProduct(productId);

        if (items == null || items.isEmpty()) {
            return getProductImages(productId);
        }

        Set<UUID> seenIds = new HashSet<>();
        for (ProductImageReorderItemRequest item : items) {
            if (!seenIds.add(item.id())) {
                throw new BadRequestException("No se permiten IDs de imágenes duplicados en la solicitud de reordenamiento");
            }
        }

        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        Map<UUID, ProductImage> imageMap = existingImages.stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        for (ProductImageReorderItemRequest item : items) {
            if (!imageMap.containsKey(item.id())) {
                throw new ResourceNotFoundException("La imagen con ID " + item.id() + " no pertenece al producto o no existe");
            }
        }

        for (ProductImageReorderItemRequest item : items) {
            imageMap.get(item.id()).setSortOrder(item.sortOrder());
        }

        productImageRepository.saveAll(existingImages);

        return getProductImages(productId);
    }

    @Transactional
    public void deleteProductImage(UUID productId, UUID imageId) {
        Product product = findProduct(productId);

        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada con ID: " + imageId + " para el producto: " + productId));

        String storagePath = image.getStoragePath();
        boolean wasPrimary = image.isPrimary();

        product.removeImage(image);
        productImageRepository.delete(image);

        // Eliminación física en Supabase Storage para evitar imágenes huérfanas
        try {
            storage.delete(productBucket, storagePath);
        } catch (Exception e) {
            log.warn("No se pudo eliminar el archivo físico en storage: bucket={}, path={}, error={}",
                    productBucket, storagePath, e.getMessage());
        }

        // Si se eliminó la imagen principal, promover la siguiente imagen disponible a principal (sortOrder = 0)
        if (wasPrimary) {
            List<ProductImage> remaining = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage newPrimary = remaining.getFirst();
                newPrimary.setSortOrder(0);
                productImageRepository.save(newPrimary);
            }
        }
    }

    @Transactional
    public void deleteProductImagesByProduct(UUID productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        for (ProductImage image : images) {
            try {
                storage.delete(productBucket, image.getStoragePath());
            } catch (Exception e) {
                log.warn("No se pudo eliminar el archivo físico en storage: bucket={}, path={}, error={}",
                        productBucket, image.getStoragePath(), e.getMessage());
            }
        }
        productImageRepository.deleteByProductId(productId);
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
    }

    private Product findPublishedProduct(String identifier) {
        Product product;
        try {
            UUID id = UUID.fromString(identifier.trim());
            product = productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + identifier));
        } catch (IllegalArgumentException e) {
            product = productRepository.findBySlug(identifier.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con slug: " + identifier));
        }

        if (product.getStatus() != ProductStatus.published) {
            throw new ResourceNotFoundException("Producto no disponible o no publicado: " + identifier);
        }
        return product;
    }

    private ProductImageResponse toResponse(ProductImage image) {
        String publicUrl = storage.getPublicUrl(productBucket, image.getStoragePath());
        return ProductImageResponse.fromEntity(image, publicUrl);
    }
}
