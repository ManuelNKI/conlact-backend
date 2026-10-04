package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.variant.ProductVariantCreateRequest;
import com.conlact.conlact_backend.dto.variant.ProductVariantResponse;
import com.conlact.conlact_backend.dto.variant.ProductVariantUpdateRequest;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductVariantService productVariantService;

    private UUID productId;
    private UUID variantId;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        product = Product.builder()
                .id(productId)
                .name("Queso Andino")
                .slug("queso-andino")
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .product(product)
                .sku("AND-CUN-250")
                .presentationName("Cuña 250g")
                .weightGrams(250)
                .price(new BigDecimal("3.50"))
                .stock(30)
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("createVariant crea y persiste exitosamente una nueva variante de producto")
    void testCreateVariant_Success() {
        ProductVariantCreateRequest request = ProductVariantCreateRequest.builder()
                .sku("and-cun-250")
                .presentationName("Cuña 250g")
                .weightGrams(250)
                .price(new BigDecimal("3.50"))
                .stock(30)
                .lowStockThreshold(5)
                .isActive(true)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsBySkuIgnoreCase("AND-CUN-250")).thenReturn(false);
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);

        ProductVariantResponse response = productVariantService.createVariant(productId, request);

        assertNotNull(response);
        assertEquals("AND-CUN-250", response.getSku());
        assertEquals("Cuña 250g", response.getPresentationName());
        assertEquals(30, response.getStock());
        assertEquals(0L, response.getVersion());
        verify(productVariantRepository).saveAndFlush(any(ProductVariant.class));
    }

    @Test
    @DisplayName("createVariant lanza ResourceNotFoundException si el producto no existe")
    void testCreateVariant_ProductNotFound() {
        ProductVariantCreateRequest request = ProductVariantCreateRequest.builder()
                .sku("AND-CUN-250")
                .presentationName("Cuña 250g")
                .price(new BigDecimal("3.50"))
                .stock(10)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productVariantService.createVariant(productId, request));
        verify(productVariantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createVariant lanza ConflictException si el SKU ya existe")
    void testCreateVariant_SkuConflict() {
        ProductVariantCreateRequest request = ProductVariantCreateRequest.builder()
                .sku("AND-CUN-250")
                .presentationName("Cuña 250g")
                .price(new BigDecimal("3.50"))
                .stock(10)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsBySkuIgnoreCase("AND-CUN-250")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> productVariantService.createVariant(productId, request));
        verify(productVariantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deductStock descuenta stock centralizado y guarda la entidad")
    void testDeductStock_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);

        ProductVariantResponse response = productVariantService.deductStock(variantId, 10);

        assertNotNull(response);
        assertEquals(20, variant.getStock());
        verify(productVariantRepository).saveAndFlush(variant);
    }

    @Test
    @DisplayName("deductStock lanza InsufficientStockException si se supera el stock disponible")
    void testDeductStock_InsufficientStock() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(InsufficientStockException.class,
                () -> productVariantService.deductStock(variantId, 50));
        verify(productVariantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("addStock incrementa stock en el inventario central")
    void testAddStock_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);

        ProductVariantResponse response = productVariantService.addStock(variantId, 20);

        assertNotNull(response);
        assertEquals(50, variant.getStock());
        verify(productVariantRepository).saveAndFlush(variant);
    }

    @Test
    @DisplayName("setStock ajusta el valor absoluto de stock")
    void testSetStock_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productVariantRepository.saveAndFlush(any(ProductVariant.class))).thenReturn(variant);

        ProductVariantResponse response = productVariantService.setStock(variantId, 100);

        assertNotNull(response);
        assertEquals(100, variant.getStock());
        verify(productVariantRepository).saveAndFlush(variant);
    }

    @Test
    @DisplayName("deactivateVariant desactiva la variante como baja lógica")
    void testDeactivateVariant_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        productVariantService.deactivateVariant(variantId);

        assertFalse(variant.getIsActive());
        verify(productVariantRepository).save(variant);
    }
}
