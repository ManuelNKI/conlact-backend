package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.DeliveryMethod;
import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.entity.enums.PaymentMethod;
import com.conlact.conlact_backend.entity.enums.PaymentStatus;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.conlact.conlact_backend.entity.enums.ReservationStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.repository.*;
import com.conlact.conlact_backend.storage.IStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private IStorage storage;

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
                storage,
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
    @DisplayName("BE-30: Debe subir comprobante de transferencia, marcar pago verificado y consumir reserva")
    void shouldUploadBankTransferProofAndConsumeReservation() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Product product = createProduct("Queso Manual");
        ProductVariant variant = createVariant(product, "MAN-500", new BigDecimal("4.25"), 8);
        Order order = Order.builder()
                .id(orderId)
                .paymentMethod(PaymentMethod.bank_transfer)
                .paymentStatus(PaymentStatus.pending)
                .status(OrderStatus.pending)
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .build();
        Payment payment = Payment.builder()
                .id(paymentId)
                .order(order)
                .method(PaymentMethod.bank_transfer)
                .status(PaymentStatus.pending)
                .amount(new BigDecimal("4.25"))
                .build();
        InventoryReservation reservation = InventoryReservation.builder()
                .order(order)
                .productVariant(variant)
                .quantity(1)
                .status(ReservationStatus.active)
                .expiresAt(order.getExpiresAt())
                .build();

        var proof = new org.springframework.mock.web.MockMultipartFile(
                "file", "comprobante.pdf", "application/pdf", "PDF".getBytes());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment));
        when(inventoryReservationRepository.findByOrderId(orderId)).thenReturn(List.of(reservation));
        when(storage.upload(eq("payment-proofs"), any(), any(byte[].class), eq("application/pdf")))
                .thenReturn("orders/" + orderId + "/proof-test.pdf");

        var response = orderService.uploadBankTransferProof(orderId, proof, "TRX-123");

        assertThat(response.paymentStatus()).isEqualTo("verified");
        assertThat(response.proofStoragePath()).isEqualTo("orders/" + orderId + "/proof-test.pdf");
        assertThat(payment.getBankReference()).isEqualTo("TRX-123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.verified);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.verified);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.consumed);
    }
}
