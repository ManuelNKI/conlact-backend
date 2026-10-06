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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private IStorage storage;

    private ProductImageService service;
    private final String bucket = "product-images";
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductImageService(productRepository, productImageRepository, storage, bucket);
        productId = UUID.randomUUID();
        product = Product.builder()
                .id(productId)
                .name("Queso Fresco El Lindero")
                .slug("queso-fresco-el-lindero")
                .status(ProductStatus.published)
                .build();
    }

    @Test
    @DisplayName("BE-20: La primera imagen agregada es automáticamente la principal (sort_order = 0, is_primary = true)")
    void shouldMakeFirstImagePrimaryAutomatically() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.existsByProductIdAndStoragePath(productId, "products/queso1.webp")).thenReturn(false);
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(Collections.emptyList());

        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage img = invocation.getArgument(0);
            img.setId(UUID.randomUUID());
            img.setCreatedAt(OffsetDateTime.now());
            return img;
        });
        when(storage.getPublicUrl(eq(bucket), eq("products/queso1.webp"))).thenReturn("https://supabase.co/products/queso1.webp");

        ProductImageCreateRequest req = new ProductImageCreateRequest(
                "products/queso1.webp",
                "Foto frontal",
                null,
                null
        );

        ProductImageResponse response = service.addProductImage(productId, req);

        assertThat(response).isNotNull();
        assertThat(response.storagePath()).isEqualTo("products/queso1.webp");
        assertThat(response.altText()).isEqualTo("Foto frontal");
        assertThat(response.sortOrder()).isEqualTo(0);
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.url()).isEqualTo("https://supabase.co/products/queso1.webp");
    }

    @Test
    @DisplayName("BE-20: Si se agrega una nueva imagen con is_primary=true, desplaza las existentes y queda en sort_order=0")
    void shouldShiftExistingImagesWhenAddingAsPrimary() {
        ProductImage existing1 = ProductImage.builder()
                .id(UUID.randomUUID())
                .product(product)
                .storagePath("products/old-cover.webp")
                .sortOrder(0)
                .build();
        ProductImage existing2 = ProductImage.builder()
                .id(UUID.randomUUID())
                .product(product)
                .storagePath("products/detail.webp")
                .sortOrder(1)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.existsByProductIdAndStoragePath(productId, "products/new-cover.webp")).thenReturn(false);
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(List.of(existing1, existing2));

        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage img = invocation.getArgument(0);
            img.setId(UUID.randomUUID());
            return img;
        });
        when(storage.getPublicUrl(any(), any())).thenReturn("https://supabase.co/img.webp");

        ProductImageCreateRequest req = new ProductImageCreateRequest(
                "products/new-cover.webp",
                "Nueva portada",
                null,
                true
        );

        ProductImageResponse response = service.addProductImage(productId, req);

        assertThat(response.sortOrder()).isEqualTo(0);
        assertThat(response.isPrimary()).isTrue();
        assertThat(existing1.getSortOrder()).isEqualTo(1);
        assertThat(existing2.getSortOrder()).isEqualTo(2);

        verify(productImageRepository).saveAll(any());
    }

    @Test
    @DisplayName("BE-20: Rechaza almacenamiento duplicado para el mismo producto (409 Conflict)")
    void shouldRejectDuplicateStoragePath() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.existsByProductIdAndStoragePath(productId, "products/duplicate.webp")).thenReturn(true);

        ProductImageCreateRequest req = new ProductImageCreateRequest("products/duplicate.webp", "Duplicado", 1, false);

        assertThatThrownBy(() -> service.addProductImage(productId, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe una fotografía asociada");
    }

    @Test
    @DisplayName("BE-20: setPrimaryImage promueve la imagen seleccionada a sort_order=0 y reordena las demás consecutivamente")
    void shouldSetExistingImageAsPrimary() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();
        ProductImage img1 = ProductImage.builder().id(img1Id).product(product).storagePath("p1.webp").sortOrder(0).build();
        ProductImage img2 = ProductImage.builder().id(img2Id).product(product).storagePath("p2.webp").sortOrder(1).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(img2Id, productId)).thenReturn(Optional.of(img2));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(List.of(img1, img2));
        when(storage.getPublicUrl(any(), eq("p2.webp"))).thenReturn("https://supabase.co/p2.webp");

        ProductImageResponse response = service.setPrimaryImage(productId, img2Id);

        assertThat(response.id()).isEqualTo(img2Id);
        assertThat(response.sortOrder()).isEqualTo(0);
        assertThat(response.isPrimary()).isTrue();
        assertThat(img1.getSortOrder()).isEqualTo(1);
        assertThat(img2.getSortOrder()).isEqualTo(0);

        verify(productImageRepository).saveAll(any());
    }

    @Test
    @DisplayName("BE-20: Reordenar galería actualiza el orden atómicamente")
    void shouldReorderProductImagesAtomically() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();
        ProductImage img1 = ProductImage.builder().id(img1Id).product(product).storagePath("p1.webp").sortOrder(0).build();
        ProductImage img2 = ProductImage.builder().id(img2Id).product(product).storagePath("p2.webp").sortOrder(1).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(List.of(img1, img2));
        when(storage.getPublicUrl(any(), any())).thenReturn("https://supabase.co/img.webp");

        List<ProductImageReorderItemRequest> items = List.of(
                new ProductImageReorderItemRequest(img1Id, 5),
                new ProductImageReorderItemRequest(img2Id, 0)
        );

        List<ProductImageResponse> result = service.reorderProductImages(productId, items);

        assertThat(img1.getSortOrder()).isEqualTo(5);
        assertThat(img2.getSortOrder()).isEqualTo(0);
        assertThat(result).hasSize(2);
        verify(productImageRepository).saveAll(any());
    }

    @Test
    @DisplayName("BE-20: Reordenar rechaza IDs duplicados en el payload (400 Bad Request)")
    void shouldRejectDuplicateIdsInReorderRequest() {
        UUID img1Id = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        List<ProductImageReorderItemRequest> items = List.of(
                new ProductImageReorderItemRequest(img1Id, 0),
                new ProductImageReorderItemRequest(img1Id, 1)
        );

        assertThatThrownBy(() -> service.reorderProductImages(productId, items))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("IDs de imágenes duplicados");
    }

    @Test
    @DisplayName("BE-20: Reordenar rechaza IDs de imágenes que pertenecen a otro producto (404 Not Found)")
    void shouldRejectForeignImageInReorderRequest() {
        UUID foreignId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(Collections.emptyList());

        List<ProductImageReorderItemRequest> items = List.of(
                new ProductImageReorderItemRequest(foreignId, 0)
        );

        assertThatThrownBy(() -> service.reorderProductImages(productId, items))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no pertenece al producto");
    }

    @Test
    @DisplayName("BE-20: Eliminar imagen borra el registro de la BD, limpia el archivo de Supabase Storage y promueve la siguiente a principal")
    void shouldDeleteImageFromDbAndStorageAndPromoteNext() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();
        ProductImage img1 = ProductImage.builder().id(img1Id).product(product).storagePath("p1.webp").sortOrder(0).build();
        ProductImage img2 = ProductImage.builder().id(img2Id).product(product).storagePath("p2.webp").sortOrder(1).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(img1Id, productId)).thenReturn(Optional.of(img1));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(List.of(img2));

        service.deleteProductImage(productId, img1Id);

        verify(productImageRepository).delete(img1);
        verify(storage).delete(bucket, "p1.webp");
        assertThat(img2.getSortOrder()).isEqualTo(0);
        verify(productImageRepository).save(img2);
    }

    @Test
    @DisplayName("BE-20: Intento de eliminar imagen inexistente lanza 404")
    void shouldThrowNotFoundWhenDeletingNonExistentImage() {
        UUID missingId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductId(missingId, productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProductImage(productId, missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Imagen no encontrada");
    }

    @Test
    @DisplayName("BE-20: deleteProductImagesByProduct elimina todas las fotos de BD y Storage")
    void shouldDeleteAllProductImagesFromDbAndStorage() {
        ProductImage img1 = ProductImage.builder().id(UUID.randomUUID()).storagePath("p1.webp").build();
        ProductImage img2 = ProductImage.builder().id(UUID.randomUUID()).storagePath("p2.webp").build();

        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId)).thenReturn(List.of(img1, img2));

        service.deleteProductImagesByProduct(productId);

        verify(storage).delete(bucket, "p1.webp");
        verify(storage).delete(bucket, "p2.webp");
        verify(productImageRepository).deleteByProductId(productId);
    }
}
