package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.product.AdminProductRequest;
import com.conlact.conlact_backend.dto.product.AdminProductResponse;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.repository.CategoryRepository;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.storage.IStorage;
import com.conlact.conlact_backend.util.SlugUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminProductService {
    private final ProductRepository productRepository;
    private final AssociationRepository associationRepository;
    private final CategoryRepository categoryRepository;
    private final IStorage storage;
    private final String productBucket;

    public AdminProductService(ProductRepository productRepository, AssociationRepository associationRepository,
                               CategoryRepository categoryRepository, IStorage storage,
                               @Value("${app.supabase.storage.product-bucket:product-images}") String productBucket) {
        this.productRepository = productRepository;
        this.associationRepository = associationRepository;
        this.categoryRepository = categoryRepository;
        this.storage = storage;
        this.productBucket = productBucket;
    }

    public List<AdminProductResponse> getAllProducts() {
        return productRepository.findAllByOrderByNameAscIdAsc().stream().map(this::toResponse).toList();
    }

    public AdminProductResponse getProductById(UUID id) {
        return toResponse(findProduct(id));
    }

    @Transactional
    public AdminProductResponse createProduct(AdminProductRequest request) {
        Product product = Product.builder()
                .slug(resolveSlug(request.slug(), request.name(), null))
                .status(request.status() == null ? ProductStatus.draft : request.status())
                .isFeatured(Boolean.TRUE.equals(request.isFeatured()))
                .build();
        applyContent(product, request);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public AdminProductResponse updateProduct(UUID id, AdminProductRequest request) {
        Product product = findProduct(id);
        if (request.slug() != null && !request.slug().isBlank()) {
            product.setSlug(resolveSlug(request.slug(), request.name(), id));
        }
        applyContent(product, request);
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.isFeatured() != null) {
            product.setIsFeatured(request.isFeatured());
        }
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void hideProduct(UUID id) {
        Product product = findProduct(id);
        product.setStatus(ProductStatus.hidden);
        productRepository.save(product);
    }

    private void applyContent(Product product, AdminProductRequest request) {
        product.setName(request.name().trim());
        product.setCheeseType(normalizeText(request.cheeseType()));
        product.setShortDescription(normalizeText(request.shortDescription()));
        product.setDescription(normalizeText(request.description()));
        product.setOriginText(normalizeText(request.originText()));
        product.setConservation(normalizeText(request.conservation()));
        product.setAssociation(request.associationId() == null ? null : associationRepository.findById(request.associationId())
                .orElseThrow(() -> new ResourceNotFoundException("Asociación no encontrada con ID: " + request.associationId())));
        product.setCategory(request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.categoryId())));
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
    }

    private String resolveSlug(String requestedSlug, String name, UUID existingId) {
        boolean explicit = requestedSlug != null && !requestedSlug.isBlank();
        String baseSlug = SlugUtils.toSlug((explicit ? requestedSlug : name).replace('_', ' '));
        if (baseSlug.isBlank()) {
            throw new BadRequestException("El nombre o slug debe contener letras o números");
        }
        String candidate = baseSlug;
        int suffix = 2;
        while (existingId == null ? productRepository.existsBySlug(candidate)
                : productRepository.existsBySlugAndIdNot(candidate, existingId)) {
            if (explicit) {
                throw new ConflictException("El slug '" + candidate + "' ya se encuentra en uso");
            }
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
    }

    private AdminProductResponse toResponse(Product product) {
        List<ProductVariant> variants = product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getWeightGrams, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductVariant::getSku).thenComparing(ProductVariant::getId))
                .toList();
        List<ProductVariant> activeVariants = variants.stream().filter(v -> Boolean.TRUE.equals(v.getIsActive())).toList();
        ProductVariant representative = activeVariants.isEmpty() ? null : activeVariants.getFirst();
        long stock = activeVariants.stream().mapToLong(ProductVariant::getStock).sum();
        Association association = product.getAssociation();
        Category category = product.getCategory();
        List<String> photos = product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
                .map(image -> storage.getPublicUrl(productBucket, image.getStoragePath())).toList();
        return new AdminProductResponse(product.getId(), product.getName(), product.getSlug(), product.getCheeseType(),
                product.getShortDescription(), product.getDescription(), product.getOriginText(), product.getConservation(),
                association == null ? null : association.getName(), association == null ? null : association.getId(),
                category == null ? null : category.getName(), category == null ? null : category.getId(),
                product.getStatus(), product.getIsFeatured(),
                representative == null ? null : representative.getWeightGrams() == null
                        ? representative.getPresentationName() : representative.getWeightGrams() + " g",
                representative == null ? null : representative.getPrice(), stock,
                product.getStatus() == ProductStatus.published && stock > 0, photos,
                variants.stream().map(AdminProductResponse.PresentationResponse::fromEntity).toList(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
