package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.DeliveryMethod;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.entity.enums.PaymentMethod;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.entity.enums.ReservationStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    private com.conlact.conlact_backend.repository.AuditLogRepository auditLogRepository;

    @Mock
    private com.conlact.conlact_backend.email.IEmailService emailService;

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
                auditLogRepository,
                emailService,
                entityManager
        );
    }

    private Product createProduct(String name) {
        return Product.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug("queso-fresco")
                .status(ProductStatus.published)
                .build();
    }

    private ProductVariant createVariant(Product product, String sku, BigDecimal price, int stock) {
        return ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(product)
                .sku(sku)
                .presentationName("Bloque 500g")
                .price(price)
                .stock(stock)
                .isActive(true)
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("BE-29: Debe crear exitosamente un pedido a domicilio con reserva de stock y cálculo de flete")
    void shouldCreateOrderDeliverySuccessfully() {
        Product product = createProduct("Queso Fresco");
        ProductVariant variant = createVariant(product, "LIND-500", new BigDecimal("3.25"), 20);
        UUID zoneId = UUID.randomUUID();

        ShippingZone zone = ShippingZone.builder()
                .id(zoneId)
                .name("Ambato Urbano")
                .deliveryFee(new BigDecimal("2.50"))
                .isActive(true)
                .build();

        when(shippingZoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            order.setOrderNumber(10042L);
            return order;
        });

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Carlos López")
                        .taxId("1803456789")
                        .phone("+593998765432")
                        .email("carlos@gmail.com")
                        .address("Ficoa, Ambato")
                        .build())
                .deliveryMethod("delivery")
                .shippingZoneId(zoneId)
                .paymentMethod("payphone")
                .clientNotes("Dejar en garita")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder()
                                .variantId(variant.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        OrderCreateResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).isEqualTo(10042L);
        assertThat(response.subtotal()).isEqualByComparingTo("6.50"); // 2 * 3.25
        assertThat(response.shippingCost()).isEqualByComparingTo("2.50");
        assertThat(response.total()).isEqualByComparingTo("9.00"); // 6.50 + 2.50
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.paymentMethod()).isEqualTo("payphone");
        assertThat(response.payphoneUrl()).contains("payphone.com.ec");
        assertThat(response.expiresAt()).isNotNull();

        // Verificar que el stock físico de la variante fue descontado de 20 a 18
        assertThat(variant.getStock()).isEqualTo(18);
        verify(productVariantRepository).save(variant);

        // Verificar que se crearon las reservas de inventario temporales
        verify(inventoryReservationRepository).saveAll(any());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("BE-29: Debe crear pedido con retiro en fábrica (pickup) con flete cero")
    void shouldCreateOrderPickupSuccessfully() {
        Product product = createProduct("Queso Maduro");
        ProductVariant variant = createVariant(product, "MAD-1000", new BigDecimal("6.50"), 10);

        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            order.setOrderNumber(10043L);
            return order;
        });

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Ana Gómez")
                        .taxId("1801234567")
                        .phone("+593987654321")
                        .address("Pilahuín")
                        .build())
                .deliveryMethod("pickup")
                .paymentMethod("transferencia")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder()
                                .variantId(variant.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        OrderCreateResponse response = orderService.createOrder(request);

        assertThat(response.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(response.subtotal()).isEqualByComparingTo("6.50");
        assertThat(response.total()).isEqualByComparingTo("6.50");
        assertThat(response.paymentMethod()).isEqualTo("transferencia");
        assertThat(response.payphoneUrl()).isNull();
        assertThat(variant.getStock()).isEqualTo(9);
    }

    @Test
    @DisplayName("BE-29: Debe rechazar pedido con InsufficientStockException si el stock no alcanza")
    void shouldThrowInsufficientStockException() {
        Product product = createProduct("Queso Escaso");
        ProductVariant variant = createVariant(product, "ESC-500", new BigDecimal("4.00"), 2);

        when(productVariantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Juan Pérez")
                        .taxId("1809999999")
                        .phone("+593991112233")
                        .build())
                .deliveryMethod("pickup")
                .paymentMethod("payphone")
                .items(List.of(
                        OrderCreateRequest.OrderItemRequest.builder()
                                .variantId(variant.getId())
                                .quantity(5) // Pide 5 pero solo hay 2
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BE-29: Debe rechazar pedido a domicilio sin zona de entrega especificada")
    void shouldThrowBadRequestWhenMissingShippingZone() {
        OrderCreateRequest request = OrderCreateRequest.builder()
                .customer(OrderCreateRequest.CustomerDto.builder()
                        .name("Cliente Sin Zona")
                        .taxId("1805555555")
                        .phone("+593994445566")
                        .build())
                .deliveryMethod("delivery")
                .shippingZoneId(null) // Falta zona
                .paymentMethod("payphone")
                .items(List.of())
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("La zona de entrega ('zona_envio_id') es obligatoria");
    }

    @Test
    @DisplayName("BE-33: Transición exitosa de PENDIENTE a EN_PREPARACION registrando auditoría en audit_log")
    void shouldTransitionOrderPendingToPreparingAndRecordAuditLog() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber(10042L)
                .status(OrderStatus.pending)
                .customerName("María Morales")
                .customerEmail("maria@example.com")
                .subtotal(new BigDecimal("10.00"))
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("10.00"))
                .deliveryMethod(DeliveryMethod.pickup)
                .paymentMethod(PaymentMethod.bank_transfer)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        com.conlact.conlact_backend.security.UserPrincipal adminPrincipal = new com.conlact.conlact_backend.security.UserPrincipal(
                UUID.fromString("ba000000-0000-0000-0000-000000000001"),
                "admin@conlact.org",
                "Admin General",
                "admin"
        );

        com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest request = com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.builder()
                .newStatus("preparing")
                .administrativeNotes("Transferencia Banco Pichincha #458921 confirmada en cuenta.")
                .build();

        var response = orderService.updateOrderStatus(orderId, request, adminPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.orderNumber()).isEqualTo(10042L);
        assertThat(response.previousStatus()).isEqualTo("pending");
        assertThat(response.currentStatus()).isEqualTo("preparing");
        assertThat(response.administrativeNotes()).contains("Transferencia Banco Pichincha #458921");

        verify(auditLogRepository).save(argThat(audit -> {
            assertThat(audit.getEntityName()).isEqualTo("orders");
            assertThat(audit.getEntityId()).isEqualTo(orderId.toString());
            assertThat(audit.getAction()).isEqualTo("ORDER_STATUS_UPDATE");
            assertThat(audit.getOldData().get("status")).isEqualTo("pending");
            assertThat(audit.getNewData().get("status")).isEqualTo("preparing");
            assertThat(audit.getNewData().get("usuario")).isEqualTo("admin@conlact.org");
            assertThat(audit.getNewData().get("rol")).isEqualTo("admin");
            assertThat(audit.getNewData().get("timestamp")).isNotNull();
            return true;
        }));
    }

    @Test
    @DisplayName("BE-33: Transición de cancelación restituye stock y registra auditoría")
    void shouldCancelOrderAndRevertStockAndRecordAuditLog() {
        UUID orderId = UUID.randomUUID();
        Product product = createProduct("Queso Fresco");
        ProductVariant variant = createVariant(product, "LIND-500", new BigDecimal("3.25"), 5);

        Order order = Order.builder()
                .id(orderId)
                .orderNumber(10043L)
                .status(OrderStatus.pending)
                .customerName("Juan Perez")
                .subtotal(new BigDecimal("6.50"))
                .shippingCost(BigDecimal.ZERO)
                .total(new BigDecimal("6.50"))
                .deliveryMethod(DeliveryMethod.pickup)
                .paymentMethod(PaymentMethod.payphone)
                .build();

        InventoryReservation reservation = InventoryReservation.builder()
                .order(order)
                .productVariant(variant)
                .quantity(2)
                .status(ReservationStatus.active)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryReservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of());

        com.conlact.conlact_backend.security.UserPrincipal adminPrincipal = new com.conlact.conlact_backend.security.UserPrincipal(
                UUID.fromString("ba000000-0000-0000-0000-000000000001"),
                "admin@conlact.org",
                "Admin General",
                "admin"
        );

        com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest request = com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.builder()
                .newStatus("cancelled")
                .administrativeNotes("Cliente solicitó cancelación")
                .build();

        var response = orderService.updateOrderStatus(orderId, request, adminPrincipal);

        assertThat(response.currentStatus()).isEqualTo("cancelled");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.released);
        assertThat(variant.getStock()).isEqualTo(7);
        verify(productVariantRepository).save(variant);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("BE-33: Debe rechazar transiciones de estado no permitidas en la máquina de estados")
    void shouldRejectInvalidStateTransition() {
        UUID orderId = UUID.randomUUID();
        Order completedOrder = Order.builder()
                .id(orderId)
                .orderNumber(10044L)
                .status(OrderStatus.completed)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));

        com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest request = com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.builder()
                .newStatus("pending")
                .build();

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Transición de estado no válida");
    }

    @Test
    @DisplayName("BE-33: Debe rechazar transición al mismo estado en el que ya se encuentra la orden")
    void shouldRejectTransitionToIdenticalStatus() {
        UUID orderId = UUID.randomUUID();
        Order preparingOrder = Order.builder()
                .id(orderId)
                .orderNumber(10045L)
                .status(OrderStatus.preparing)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(preparingOrder));

        com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest request = com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.builder()
                .newStatus("preparing")
                .build();

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, request, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("El pedido ya se encuentra en el estado 'preparing'");
    }

    @Test
    @DisplayName("BE-33: Debe parsear correctamente estados en español con mayúsculas y acentos")
    void shouldParseSpanishStatusInputsCorrectly() {
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("PENDIENTE")).isEqualTo(OrderStatus.pending);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("PAGADO")).isEqualTo(OrderStatus.paid);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("EN_PREPARACION")).isEqualTo(OrderStatus.preparing);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("EN_PREPARACIÓN")).isEqualTo(OrderStatus.preparing);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("ENVIADO")).isEqualTo(OrderStatus.shipped);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("ENTREGADO")).isEqualTo(OrderStatus.completed);
        assertThat(com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest.parseStatus("CANCELADO")).isEqualTo(OrderStatus.cancelled);
    }
}
