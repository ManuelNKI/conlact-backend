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
import com.conlact.conlact_backend.storage.IStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IStorage storage;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, storage, "product-images");
    }

    private Product createSampleProduct(String name, String slug, ProductStatus status, int stock) {
        UUID prodId = UUID.randomUUID();
        Association assoc = Association.builder()
                .id(UUID.randomUUID())
                .name("Asociación El Lindero")
                .slug("el-lindero")
                .build();

        Category cat = Category.builder()
                .id(UUID.randomUUID())
                .name("Queso Fresco")
                .slug("fresco")
                .build();

        Product product = Product.builder()
                .id(prodId)
                .name(name)
                .slug(slug)
                .cheeseType("Fresco Semidesnatado")
                .shortDescription("Queso artesanal")
                .description("Descripción larga")
                .originText("Pilahuín")
                .conservation("2°C a 6°C")
                .status(status)
                .isFeatured(true)
                .association(assoc)
                .category(cat)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();

        ProductVariant variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(product)
                .sku("LIND-QFR-500")
                .presentationName("Bloque 500 g empacado al vacío")
                .weightGrams(500)
                .price(new BigDecimal("3.25"))
                .stock(stock)
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .build();
        product.getVariants().add(variant);

        ProductImage image = ProductImage.builder()
                .id(UUID.randomUUID())
                .product(product)
                .storagePath("products/sample.jpg")
                .sortOrder(1)
                .build();
        product.getImages().add(image);

        return product;
    }

    @Test
    @DisplayName("BE-17: Debe listar productos publicados con formato del catálogo TypeScript")
    void shouldListPublishedProducts() {
        Product prod1 = createSampleProduct("Queso Fresco", "queso-fresco", ProductStatus.published, 20);
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(prod1));
        when(storage.getPublicUrl(eq("product-images"), eq("products/sample.jpg")))
                .thenReturn("https://supabase.co/storage/v1/object/public/product-images/products/sample.jpg");

        List<ProductPublicResponse> result = productService.getPublishedProducts(null, null, null, null, null);

        assertThat(result).hasSize(1);
        ProductPublicResponse response = result.getFirst();
        assertThat(response.name()).isEqualTo("Queso Fresco");
        assertThat(response.slug()).isEqualTo("queso-fresco");
        assertThat(response.available()).isTrue();
        assertThat(response.stock()).isEqualTo(20);
        assertThat(response.weight()).isEqualTo("500 g");
        assertThat(response.price()).isEqualByComparingTo("3.25");
        assertThat(response.associationName()).isEqualTo("Asociación El Lindero");
        assertThat(response.photos()).containsExactly("https://supabase.co/storage/v1/object/public/product-images/products/sample.jpg");
        assertThat(response.presentations()).hasSize(1);
        assertThat(response.presentations().getFirst().sku()).isEqualTo("LIND-QFR-500");
    }

    @Test
    @DisplayName("BE-17: Debe obtener detalle de producto publicado por UUID")
    void shouldGetProductByUuid() {
        Product prod = createSampleProduct("Queso Fresco", "queso-fresco", ProductStatus.published, 15);
        UUID id = prod.getId();

        when(productRepository.findById(id)).thenReturn(Optional.of(prod));
        when(storage.getPublicUrl(any(), any())).thenReturn("https://supabase.co/img.jpg");

        ProductPublicResponse response = productService.getPublishedProductByIdOrSlug(id.toString());

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Queso Fresco");
        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("BE-17: Debe obtener detalle de producto publicado por slug")
    void shouldGetProductBySlug() {
        Product prod = createSampleProduct("Queso Fresco", "queso-fresco", ProductStatus.published, 15);

        when(productRepository.findBySlug("queso-fresco")).thenReturn(Optional.of(prod));
        when(storage.getPublicUrl(any(), any())).thenReturn("https://supabase.co/img.jpg");

        ProductPublicResponse response = productService.getPublishedProductByIdOrSlug("queso-fresco");

        assertThat(response.slug()).isEqualTo("queso-fresco");
    }

    @Test
    @DisplayName("BE-17: Debe lanzar 404 si el producto no existe o está en borrador/oculto")
    void shouldThrowNotFoundWhenNotPublishedOrNonExistent() {
        Product draftProd = createSampleProduct("Queso Borrador", "queso-borrador", ProductStatus.draft, 10);
        when(productRepository.findBySlug("queso-borrador")).thenReturn(Optional.of(draftProd));

        assertThatThrownBy(() -> productService.getPublishedProductByIdOrSlug("queso-borrador"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no disponible o no publicado");

        when(productRepository.findBySlug("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getPublishedProductByIdOrSlug("inexistente"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
