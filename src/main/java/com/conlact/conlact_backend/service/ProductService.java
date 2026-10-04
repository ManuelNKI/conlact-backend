package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.product.ProductPublicResponse;
import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.Category;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductImage;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.specification.ProductSpecifications;
import com.conlact.conlact_backend.storage.IStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final IStorage storage;
    private final String productBucket;

    public ProductService(ProductRepository productRepository,
                          IStorage storage,
                          @Value("${app.supabase.storage.product-bucket:product-images}") String productBucket) {
        this.productRepository = productRepository;
        this.storage = storage;
        this.productBucket = productBucket;
    }

    public List<ProductPublicResponse> getPublishedProducts(String association,
                                                            String category,
                                                            Boolean featured,
                                                            Boolean available,
                                                            String search) {
        Specification<Product> spec = Specification.where(ProductSpecifications.isPublished());

        if (association != null && !association.isBlank()) {
            spec = spec.and(ProductSpecifications.byAssociation(association));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and(ProductSpecifications.byCategory(category));
        }
        if (featured != null) {
            spec = spec.and(ProductSpecifications.isFeatured(featured));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(ProductSpecifications.searchByText(search));
        }
        if (Boolean.TRUE.equals(available)) {
            spec = spec.and(ProductSpecifications.hasAvailableStock());
        }

        List<Product> products = productRepository.findAll(spec);

        return products.stream()
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPublicResponse)
                .toList();
    }

    public ProductPublicResponse getPublishedProductByIdOrSlug(String identifier) {
        Product product;
        try {
            UUID id = UUID.fromString(identifier.trim());
            product = productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con identificador: " + identifier));
        } catch (IllegalArgumentException e) {
            product = productRepository.findBySlug(identifier.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con slug: " + identifier));
        }

        if (product.getStatus() != ProductStatus.published) {
            throw new ResourceNotFoundException("Producto no disponible o no publicado: " + identifier);
        }

        return toPublicResponse(product);
    }

    private ProductPublicResponse toPublicResponse(Product product) {
        List<ProductVariant> variants = product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getWeightGrams, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductVariant::getSku)
                        .thenComparing(ProductVariant::getId))
                .toList();

        List<ProductVariant> activeVariants = variants.stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .toList();

        ProductVariant representative = activeVariants.isEmpty() ? null : activeVariants.getFirst();
        long stock = activeVariants.stream().mapToLong(ProductVariant::getStock).sum();

        Association association = product.getAssociation();
        Category category = product.getCategory();

        List<String> photos = product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
                .map(image -> storage.getPublicUrl(productBucket, image.getStoragePath()))
                .toList();

        String weightText = null;
        if (representative != null) {
            weightText = representative.getWeightGrams() != null
                    ? representative.getWeightGrams() + " g"
                    : representative.getPresentationName();
        }

        return ProductPublicResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .cheeseType(product.getCheeseType())
                .weight(weightText)
                .price(representative != null ? representative.getPrice() : null)
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .originText(product.getOriginText())
                .conservation(product.getConservation())
                .photos(photos)
                .associationName(association != null ? association.getName() : null)
                .associationId(association != null ? association.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryId(category != null ? category.getId() : null)
                .stock(stock)
                .available(product.getStatus() == ProductStatus.published && stock > 0)
                .presentations(activeVariants.stream()
                        .map(ProductPublicResponse.PresentationResponse::fromEntity)
                        .toList())
                .build();
    }
}
