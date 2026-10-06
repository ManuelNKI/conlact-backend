package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.product.AdminProductRequest;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductImage;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.*;
import com.conlact.conlact_backend.storage.IStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {
    @Mock private ProductRepository products;
    @Mock private AssociationRepository associations;
    @Mock private CategoryRepository categories;
    @Mock private IStorage storage;
    private AdminProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(products, associations, categories, storage, "product-images");
    }

    private AdminProductRequest request(String name, String slug, UUID associationId) {
        return new AdminProductRequest(associationId, null, name, slug, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("BE-19: Genera un slug único y crea productos en borrador por defecto")
    void shouldGenerateUniqueSlug() {
        when(products.existsBySlug("queso-andino")).thenReturn(true);
        when(products.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createProduct(request(" Queso Andino ", null, null));
        assertThat(response.slug()).isEqualTo("queso-andino-2");
        assertThat(response.name()).isEqualTo("Queso Andino");
        assertThat(response.status()).isEqualTo(ProductStatus.draft);
        assertThat(response.available()).isFalse();
        assertThat(response.stock()).isZero();
    }

    @Test
    @DisplayName("BE-19: Rechaza un slug explícito duplicado y nombres que no permiten generar slug")
    void shouldRejectDuplicateOrEmptySlug() {
        when(products.existsBySlug("queso-andino")).thenReturn(true);
        assertThatThrownBy(() -> service.createProduct(request("Queso", "QUESO ANDINO", null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.createProduct(request("!!!", null, null))).isInstanceOf(BadRequestException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BE-19: Verifica las asociaciones referenciadas antes de persistir")
    void shouldRejectUnknownAssociation() {
        UUID id = UUID.randomUUID();
        when(associations.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createProduct(request("Queso", null, id)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BE-19: Actualizar el nombre conserva slug, publicación y variantes existentes")
    void shouldPreserveSlugAndVariantsOnUpdate() {
        Product product = Product.builder().id(UUID.randomUUID()).slug("queso-original")
                .status(ProductStatus.published).isFeatured(true).build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).sku("ORIGINAL-500")
                .presentationName("500 g").weightGrams(500).price(new BigDecimal("2.50")).stock(7).build();
        product.addVariant(variant);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(products.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.updateProduct(product.getId(), request("Nombre Nuevo", null, null));
        assertThat(response.slug()).isEqualTo("queso-original");
        assertThat(response.status()).isEqualTo(ProductStatus.published);
        assertThat(response.isFeatured()).isTrue();
        assertThat(response.variants()).extracting(v -> v.id()).containsExactly(variant.getId());
        assertThat(response.stock()).isEqualTo(7);
    }

    @Test
    @DisplayName("BE-19: El stock agregado ignora variantes inactivas y las fotos usan IStorage")
    void shouldMapActiveStockAndStorageUrls() {
        Product product = Product.builder().id(UUID.randomUUID()).status(ProductStatus.published).build();
        ProductVariant active = ProductVariant.builder().id(UUID.randomUUID()).sku("ACT-500")
                .presentationName("500 g").weightGrams(500).price(new BigDecimal("2.50")).stock(7).build();
        ProductVariant inactive = ProductVariant.builder().id(UUID.randomUUID()).sku("OLD-1000")
                .presentationName("1 kg").weightGrams(1000).price(new BigDecimal("5.00")).stock(50).isActive(false).build();
        product.addVariant(inactive);
        product.addVariant(active);
        product.setImages(List.of(ProductImage.builder().id(UUID.randomUUID()).storagePath("queso/frontal.webp").sortOrder(0).build()));
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        when(storage.getPublicUrl("product-images", "queso/frontal.webp")).thenReturn("https://example.com/frontal.webp");
        var response = service.getProductById(product.getId());
        assertThat(response.stock()).isEqualTo(7);
        assertThat(response.available()).isTrue();
        assertThat(response.price()).isEqualByComparingTo("2.50");
        assertThat(response.weight()).isEqualTo("500 g");
        assertThat(response.variants()).hasSize(2);
        assertThat(response.photos()).containsExactly("https://example.com/frontal.webp");
    }

    @Test
    @DisplayName("BE-19: Eliminar un producto oculta el registro sin borrar su inventario")
    void shouldHideWithoutPhysicalDeletion() {
        Product product = Product.builder().id(UUID.randomUUID()).status(ProductStatus.published).build();
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
        service.hideProduct(product.getId());
        assertThat(product.getStatus()).isEqualTo(ProductStatus.hidden);
        verify(products).save(product);
        verify(products, never()).delete(any(Product.class));
    }
}
