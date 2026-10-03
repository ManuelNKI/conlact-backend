package com.conlact.conlact_backend.entity;

import com.conlact.conlact_backend.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductVariantTest {

    @Test
    @DisplayName("Debe modelar correctamente variantes con distintas presentaciones (cuña, entero, rallado)")
    void testVariantPresentations() {
        ProductVariant cuna250g = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("PAR-CUN-250")
                .presentationName("Cuña 250g")
                .weightGrams(250)
                .price(new BigDecimal("3.50"))
                .stock(50)
                .lowStockThreshold(5)
                .isActive(true)
                .version(0L)
                .build();

        ProductVariant entero1kg = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("PAR-ENT-1000")
                .presentationName("Entero 1kg")
                .weightGrams(1000)
                .price(new BigDecimal("12.00"))
                .stock(20)
                .lowStockThreshold(3)
                .isActive(true)
                .version(0L)
                .build();

        ProductVariant rallado200g = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("PAR-RAL-200")
                .presentationName("Rallado 200g")
                .weightGrams(200)
                .price(new BigDecimal("2.75"))
                .stock(100)
                .lowStockThreshold(10)
                .isActive(true)
                .version(0L)
                .build();

        assertEquals("Cuña 250g", cuna250g.getPresentationName());
        assertEquals("PAR-CUN-250", cuna250g.getSku());
        assertEquals(250, cuna250g.getWeightGrams());

        assertEquals("Entero 1kg", entero1kg.getPresentationName());
        assertEquals("PAR-ENT-1000", entero1kg.getSku());

        assertEquals("Rallado 200g", rallado200g.getPresentationName());
        assertEquals("PAR-RAL-200", rallado200g.getSku());
    }

    @Test
    @DisplayName("hasAvailableStock debe retornar true si hay suficiente stock")
    void testHasAvailableStock() {
        ProductVariant variant = ProductVariant.builder()
                .stock(15)
                .build();

        assertTrue(variant.hasAvailableStock(5));
        assertTrue(variant.hasAvailableStock(15));
        assertFalse(variant.hasAvailableStock(16));
        assertFalse(variant.hasAvailableStock(0));
        assertFalse(variant.hasAvailableStock(-5));
    }

    @Test
    @DisplayName("isLowStock debe retornar true cuando el stock es menor o igual al umbral")
    void testIsLowStock() {
        ProductVariant variant = ProductVariant.builder()
                .stock(5)
                .lowStockThreshold(5)
                .build();

        assertTrue(variant.isLowStock());

        variant.setStock(4);
        assertTrue(variant.isLowStock());

        variant.setStock(6);
        assertFalse(variant.isLowStock());
    }

    @Test
    @DisplayName("deductStock descuenta correctamente el stock cuando hay disponibilidad")
    void testDeductStock_Success() {
        ProductVariant variant = ProductVariant.builder()
                .sku("TEST-SKU")
                .presentationName("Cuña 250g")
                .stock(20)
                .build();

        variant.deductStock(5);
        assertEquals(15, variant.getStock());

        variant.deductStock(15);
        assertEquals(0, variant.getStock());
    }

    @Test
    @DisplayName("deductStock lanza InsufficientStockException si se solicita más del stock disponible")
    void testDeductStock_InsufficientStock() {
        ProductVariant variant = ProductVariant.builder()
                .sku("TEST-SKU")
                .presentationName("Cuña 250g")
                .stock(10)
                .build();

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> variant.deductStock(11)
        );

        assertTrue(exception.getMessage().contains("Stock insuficiente"));
        assertTrue(exception.getMessage().contains("TEST-SKU"));
        assertEquals(10, variant.getStock());
    }

    @Test
    @DisplayName("deductStock lanza IllegalArgumentException con cantidad menor o igual a cero")
    void testDeductStock_InvalidQuantity() {
        ProductVariant variant = ProductVariant.builder()
                .stock(10)
                .build();

        assertThrows(IllegalArgumentException.class, () -> variant.deductStock(0));
        assertThrows(IllegalArgumentException.class, () -> variant.deductStock(-1));
    }

    @Test
    @DisplayName("addStock incrementa el stock correctamente")
    void testAddStock_Success() {
        ProductVariant variant = ProductVariant.builder()
                .stock(10)
                .build();

        variant.addStock(15);
        assertEquals(25, variant.getStock());
    }

    @Test
    @DisplayName("addStock lanza IllegalArgumentException con cantidad menor o igual a cero")
    void testAddStock_InvalidQuantity() {
        ProductVariant variant = ProductVariant.builder()
                .stock(10)
                .build();

        assertThrows(IllegalArgumentException.class, () -> variant.addStock(0));
        assertThrows(IllegalArgumentException.class, () -> variant.addStock(-3));
    }

    @Test
    @DisplayName("updateStock actualiza el stock absoluto")
    void testUpdateStock_Success() {
        ProductVariant variant = ProductVariant.builder()
                .stock(10)
                .build();

        variant.updateStock(40);
        assertEquals(40, variant.getStock());

        variant.updateStock(0);
        assertEquals(0, variant.getStock());

        assertThrows(IllegalArgumentException.class, () -> variant.updateStock(-5));
    }
}
