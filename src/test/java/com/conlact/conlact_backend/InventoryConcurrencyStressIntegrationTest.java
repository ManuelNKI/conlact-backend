package com.conlact.conlact_backend;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.entity.Category;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.repository.CategoryRepository;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.ProductVariantRepository;
import com.conlact.conlact_backend.service.OrderService;
import com.conlact.conlact_backend.service.ProductVariantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConcurrencyStressIntegrationTest extends AdminApiIntegrationSupport {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("BE-21: Estrés de concurrencia - 30 hilos compiten por 10 unidades de stock; certificar ausencia de sobreventa (overselling)")
    void shouldPreventOversellingUnderHighConcurrencyDeductStock() throws Exception {
        // Preparar categoría, producto y variante con stock = 10
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Product product = Product.builder()
                .name("Queso de Prueba Concurrencia")
                .slug("queso-prueba-concurrencia-" + UUID.randomUUID())
                .cheeseType("Fresco")
                .category(category)
                .status(ProductStatus.published)
                .build();
        product = productRepository.saveAndFlush(product);

        final int INITIAL_STOCK = 10;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("STRESS-SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .presentationName("Bloque 500g")
                .price(new BigDecimal("4.50"))
                .stock(INITIAL_STOCK)
                .lowStockThreshold(3)
                .isActive(true)
                .version(0L)
                .build();
        variant = productVariantRepository.saveAndFlush(variant);
        final UUID variantId = variant.getId();

        // 30 hilos simultáneos intentando descontar 1 unidad
        final int THREAD_COUNT = 30;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successfulDeductions = new AtomicInteger(0);
        AtomicInteger rejectedDeductions = new AtomicInteger(0);
        AtomicInteger concurrencyConflicts = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Disparo simultáneo de todos los hilos
                    productVariantService.deductStock(variantId, 1);
                    successfulDeductions.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejectedDeductions.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    concurrencyConflicts.incrementAndGet();
                } catch (Exception e) {
                    rejectedDeductions.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // INICIAR CONCURRENCIA
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Verificar el estado final persistido en la base de datos
        ProductVariant finalVariant = productVariantRepository.findById(variantId).orElseThrow();
        int finalStock = finalVariant.getStock();

        // CERTIFICACIONES CLAVE:
        // 1. El stock final jamás puede ser negativo (ausencia total de sobreventa)
        assertThat(finalStock)
                .as("El stock nunca debe descender de cero (NO overselling)")
                .isGreaterThanOrEqualTo(0);

        // 2. Las deducciones exitosas no pueden superar el stock original
        assertThat(successfulDeductions.get())
                .as("Las compras exitosas no pueden superar el stock inicial de 10")
                .isLessThanOrEqualTo(INITIAL_STOCK);

        // 3. Ley de conservación de inventario: Stock final + exitosos == Stock inicial
        assertThat(finalStock + successfulDeductions.get())
                .as("La suma de stock remanente y unidades descontadas debe coincidir exactamente con el inventario inicial")
                .isEqualTo(INITIAL_STOCK);

        // 4. Los intentos restantes fueron rechazados de forma segura
        assertThat(rejectedDeductions.get() + concurrencyConflicts.get())
                .as("Las solicitudes que excedieron stock o colisionaron deben ser rechazadas")
                .isEqualTo(THREAD_COUNT - successfulDeductions.get());
    }

    @Test
    @DisplayName("BE-21: Estrés transaccional en creación de pedidos - 15 hilos compiten por 4 unidades de inventario")
    void shouldPreventOversellingDuringConcurrentOrders() throws Exception {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Product product = Product.builder()
                .name("Queso Maduro Concurrente")
                .slug("queso-maduro-concurrente-" + UUID.randomUUID())
                .cheeseType("Maduro")
                .category(category)
                .status(ProductStatus.published)
                .build();
        product = productRepository.saveAndFlush(product);

        final int INITIAL_STOCK = 4;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("ORDER-STRESS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .presentationName("Rueda 1kg")
                .price(new BigDecimal("12.00"))
                .stock(INITIAL_STOCK)
                .lowStockThreshold(2)
                .isActive(true)
                .version(0L)
                .build();
        variant = productVariantRepository.saveAndFlush(variant);
        final UUID variantId = variant.getId();

        final int ORDER_ATTEMPTS = 15;
        ExecutorService executor = Executors.newFixedThreadPool(ORDER_ATTEMPTS);
        CountDownLatch readyLatch = new CountDownLatch(ORDER_ATTEMPTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(ORDER_ATTEMPTS);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger rejectedOrders = new AtomicInteger(0);

        for (int i = 0; i < ORDER_ATTEMPTS; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OrderCreateRequest request = OrderCreateRequest.builder()
                            .customer(OrderCreateRequest.CustomerDto.builder()
                                    .name("Cliente Concurrente " + index)
                                    .taxId("180123456" + (index % 10))
                                    .phone("09912345" + (index % 100))
                                    .email("cliente" + index + "@test.com")
                                    .address("Ambato")
                                    .build())
                            .deliveryMethod("pickup")
                            .paymentMethod("transferencia")
                            .items(List.of(
                                    OrderCreateRequest.OrderItemRequest.builder()
                                            .variantId(variantId)
                                            .quantity(1)
                                            .build()
                            ))
                            .build();
                    orderService.createOrder(request);
                    successfulOrders.incrementAndGet();
                } catch (InsufficientStockException | ObjectOptimisticLockingFailureException e) {
                    rejectedOrders.incrementAndGet();
                } catch (Exception e) {
                    rejectedOrders.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        ProductVariant finalVariant = productVariantRepository.findById(variantId).orElseThrow();
        int finalStock = finalVariant.getStock();

        assertThat(finalStock).isGreaterThanOrEqualTo(0);
        assertThat(successfulOrders.get()).isLessThanOrEqualTo(INITIAL_STOCK);
        assertThat(finalStock + successfulOrders.get()).isEqualTo(INITIAL_STOCK);
        assertThat(rejectedOrders.get()).isEqualTo(ORDER_ATTEMPTS - successfulOrders.get());
    }
}
