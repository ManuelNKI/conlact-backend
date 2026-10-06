package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.*;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialAuditOrderTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ShippingZoneRepository shippingZoneRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EntityManager entityManager;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                productVariantRepository,
                shippingZoneRepository,
                inventoryReservationRepository,
                paymentRepository,
                entityManager
        );
    }

    private Product createProduct(String name) {
        return Product.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-"))
                .status(ProductStatus.published)
                .build();
    }

    private ProductVariant createVariant(Product product, String presentation, BigDecimal price, int stock) {
        return ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(product)
                .presentationName(presentation)
                .sku("SKU-" + presentation.toUpperCase())
                .price(price)
                .stock(stock)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("BE-31: Auditoría de centavos - Cálculo exacto de subtotal multilínea con decimales y costo de envío")
    void testPennyExactCalculations_MultiItemWithShipping() {
        Product cheese = createProduct("Queso Andino");
        Product yogurt = createProduct("Yogurt Frutos Rojos");
        Product butter = createProduct("Mantequilla de Campo");

        // Precios con centavos específicos
        ProductVariant var1 = createVariant(cheese, "500g", new BigDecimal("3.45"), 20);
        ProductVariant var2 = createVariant(yogurt, "1L", new BigDecimal("4.99"), 20);
        ProductVariant var3 = createVariant(butter, "250g", new BigDecimal("2.35"), 20);

        when(productVariantRepository.findById(var1.getId())).thenReturn(Optional.of(var1));
        when(productVariantRepository.findById(var2.getId())).thenReturn(Optional.of(var2));
        when(productVariantRepository.findById(var3.getId())).thenReturn(Optional.of(var3));

        UUID zoneId = UUID.randomUUID();
        ShippingZone zone = ShippingZone.builder()
                .id(zoneId)
                .name("Riobamba Centro")
                .deliveryFee(new BigDecimal("2.50"))
                .isActive(true)
                .build();
        when(shippingZoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setOrderNumber(5001L);
            return o;
        });

        // 3 unidades de $3.45 = $10.35
        // 2 unidades de $4.99 = $9.98
        // 4 unidades de $2.35 = $9.40
        // Subtotal esperado: $10.35 + $9.98 + $9.40 = $29.73
        // Costo de envío: $2.50
        // Total esperado: $29.73 + $2.50 = $32.23
        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Carlos Vera")
                        .taxId("0601234567")
                        .phone("0991122334")
                        .email("carlos@test.com")
                        .address("Av. Daniel León Borja")
                        .build())
                .deliveryMethod("delivery")
                .shippingZoneId(zoneId)
                .shippingAddress("Av. Daniel León Borja y Brasil")
                .paymentMethod("payphone")
                .clientNotes("Favor entregar por la mañana")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var1.getId()).quantity(3).build(),
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var2.getId()).quantity(2).build(),
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var3.getId()).quantity(4).build()
                ))
                .build();

        OrderCreateResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.subtotal()).isEqualByComparingTo("29.73");
        assertThat(response.shippingCost()).isEqualByComparingTo("2.50");
        assertThat(response.total()).isEqualByComparingTo("32.23");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getSubtotal()).isEqualByComparingTo("29.73");
        assertThat(savedOrder.getShippingCost()).isEqualByComparingTo("2.50");
        assertThat(savedOrder.getTotal()).isEqualByComparingTo("32.23");
    }

    @Test
    @DisplayName("BE-31: Auditoría de centavos - Prevención de distorsión IEEE 754 (floating point drift)")
    void testAvoidFloatingPointDrift() {
        Product prod = createProduct("Quesillo Fresco");
        // Precios decimales propensos a deriva binaria: $0.10 y $0.20
        ProductVariant var1 = createVariant(prod, "Muestra 1", new BigDecimal("0.10"), 100);
        ProductVariant var2 = createVariant(prod, "Muestra 2", new BigDecimal("0.20"), 100);

        when(productVariantRepository.findById(var1.getId())).thenReturn(Optional.of(var1));
        when(productVariantRepository.findById(var2.getId())).thenReturn(Optional.of(var2));

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        // 10 unidades de $0.10 = $1.00
        // 10 unidades de $0.20 = $2.00
        // Total exacto: $3.00 (sin 3.0000000000000004)
        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Audit Test")
                        .taxId("0999999999")
                        .phone("0987654321")
                        .email("audit@test.com")
                        .address("Dirección")
                        .build())
                .deliveryMethod("pickup")
                .paymentMethod("payphone")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var1.getId()).quantity(10).build(),
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var2.getId()).quantity(10).build()
                ))
                .build();

        OrderCreateResponse response = orderService.createOrder(request);

        assertThat(response.subtotal()).isEqualByComparingTo("3.00");
        assertThat(response.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(response.total()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("BE-31: Entrega por retiro en punto (pickup) certifica costo de envío exactamente $0.00")
    void testPickupShippingCostIsZero() {
        Product prod = createProduct("Manjar Artesanal");
        ProductVariant var = createVariant(prod, "Pote 250g", new BigDecimal("1.75"), 50);

        when(productVariantRepository.findById(var.getId())).thenReturn(Optional.of(var));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Retiro Cliente")
                        .taxId("0102030405")
                        .phone("0987654321")
                        .email("retiro@test.com")
                        .build())
                .deliveryMethod("pickup")
                .paymentMethod("transferencia")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder().variantId(var.getId()).quantity(4).build()
                ))
                .build();

        OrderCreateResponse response = orderService.createOrder(request);

        assertThat(response.subtotal()).isEqualByComparingTo("7.00");
        assertThat(response.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(response.total()).isEqualByComparingTo("7.00");
    }

    @Test
    @DisplayName("BE-31: Rechazo si zona de entrega para delivery está inactiva")
    void testRejectsInactiveShippingZone() {
        UUID zoneId = UUID.randomUUID();
        ShippingZone inactiveZone = ShippingZone.builder()
                .id(zoneId)
                .name("Zona Cerrada")
                .deliveryFee(new BigDecimal("3.00"))
                .isActive(false)
                .build();
        when(shippingZoneRepository.findById(zoneId)).thenReturn(Optional.of(inactiveZone));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Cliente")
                        .taxId("1710000000")
                        .phone("0991122334")
                        .email("cli@test.com")
                        .address("Dirección")
                        .build())
                .deliveryMethod("delivery")
                .shippingZoneId(zoneId)
                .shippingAddress("Dirección de prueba")
                .paymentMethod("payphone")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder().variantId(UUID.randomUUID()).quantity(1).build()
                ))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no se encuentra activa");
    }
}
