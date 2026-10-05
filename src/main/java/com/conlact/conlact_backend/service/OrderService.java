package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.*;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShippingZoneRepository shippingZoneRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final PaymentRepository paymentRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        log.info("Iniciando creación transaccional de pedido para cliente: {}", request.customer().name());

        // 1. Validar y resolver método de entrega
        DeliveryMethod deliveryMethod = parseDeliveryMethod(request.deliveryMethod());
        ShippingZone shippingZone = null;
        BigDecimal shippingCost = BigDecimal.ZERO;
        String shippingAddress = null;

        if (deliveryMethod == DeliveryMethod.delivery) {
            if (request.shippingZoneId() == null) {
                throw new BadRequestException("La zona de entrega ('zona_envio_id') es obligatoria para envíos a domicilio");
            }
            shippingZone = shippingZoneRepository.findById(request.shippingZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zona de entrega no encontrada con ID: " + request.shippingZoneId()));

            if (!Boolean.TRUE.equals(shippingZone.getIsActive())) {
                throw new BadRequestException("La zona de entrega seleccionada no se encuentra activa");
            }

            shippingCost = shippingZone.getDeliveryFee() != null ? shippingZone.getDeliveryFee() : BigDecimal.ZERO;

            shippingAddress = request.shippingAddress() != null && !request.shippingAddress().isBlank()
                    ? request.shippingAddress().trim()
                    : (request.customer().address() != null ? request.customer().address().trim() : null);

            if (shippingAddress == null || shippingAddress.isBlank()) {
                throw new BadRequestException("La dirección de entrega es obligatoria para envíos a domicilio");
            }
        }

        // 2. Validar método de pago
        PaymentMethod paymentMethod = parsePaymentMethod(request.paymentMethod());

        // 3. Validar items y disponibilidad de stock
        List<OrderItem> orderItems = new ArrayList<>();
        List<InventoryReservation> reservations = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);

        for (OrderCreateRequest.OrderItemRequest itemReq : request.items()) {
            ProductVariant variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variante de producto no encontrada con ID: " + itemReq.variantId()));

            if (!Boolean.TRUE.equals(variant.getIsActive())) {
                throw new BadRequestException("La presentación '" + variant.getPresentationName() + "' no está activa para la venta");
            }

            Product product = variant.getProduct();
            if (product == null || product.getStatus() != ProductStatus.published) {
                throw new BadRequestException("El producto correspondiente a la variante '" + variant.getPresentationName() + "' no se encuentra publicado");
            }

            int requestedQuantity = itemReq.quantity();
            if (!variant.hasAvailableStock(requestedQuantity)) {
                throw new InsufficientStockException(String.format(
                        "Stock insuficiente para '%s - %s'. Solicitado: %d, disponible: %d",
                        product.getName(), variant.getPresentationName(), requestedQuantity, variant.getStock()
                ));
            }

            // Descontar del stock físico para garantizar reserva efectiva
            variant.deductStock(requestedQuantity);
            productVariantRepository.save(variant);

            BigDecimal linePrice = variant.getPrice();
            BigDecimal itemSubtotal = linePrice.multiply(BigDecimal.valueOf(requestedQuantity));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productVariant(variant)
                    .productNameSnapshot(product.getName())
                    .presentationSnapshot(variant.getPresentationName())
                    .skuSnapshot(variant.getSku())
                    .unitPrice(linePrice)
                    .quantity(requestedQuantity)
                    .build();

            orderItems.add(orderItem);
        }

        // 4. Calcular total
        BigDecimal total = subtotal.add(shippingCost);

        // 5. Construir y guardar Order
        Order order = Order.builder()
                .customerName(request.customer().name().trim())
                .customerTaxId(request.customer().taxId().trim())
                .customerPhone(request.customer().phone().trim())
                .customerEmail(request.customer().email() != null && !request.customer().email().isBlank()
                        ? request.customer().email().trim() : null)
                .billingAddress(request.customer().address() != null && !request.customer().address().isBlank()
                        ? request.customer().address().trim() : null)
                .deliveryMethod(deliveryMethod)
                .shippingZone(shippingZone)
                .shippingAddress(shippingAddress)
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .total(total)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.pending)
                .status(OrderStatus.pending)
                .expiresAt(expiresAt)
                .administrativeNotes(request.clientNotes() != null && !request.clientNotes().isBlank()
                        ? request.clientNotes().trim() : null)
                .build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
            order.getItems().add(item);
        }

        Order savedOrder = orderRepository.saveAndFlush(order);

        // Forzar sincronización con la base de datos para recuperar order_number generado por secuencia
        entityManager.refresh(savedOrder);

        // 6. Crear reservas de inventario temporales
        for (OrderItem item : orderItems) {
            InventoryReservation reservation = InventoryReservation.builder()
                    .order(savedOrder)
                    .productVariant(item.getProductVariant())
                    .quantity(item.getQuantity())
                    .status(ReservationStatus.active)
                    .expiresAt(expiresAt)
                    .build();
            reservations.add(reservation);
        }
        inventoryReservationRepository.saveAll(reservations);

        // 7. Crear registro inicial de pago
        Payment payment = Payment.builder()
                .order(savedOrder)
                .method(paymentMethod)
                .status(PaymentStatus.pending)
                .amount(savedOrder.getTotal())
                .build();
        paymentRepository.save(payment);

        // 8. Preparar URL de pasarela si aplica (PayPhone)
        String payphoneUrl = null;
        if (paymentMethod == PaymentMethod.payphone) {
            String shortId = savedOrder.getId().toString().replace("-", "").substring(0, 10);
            payphoneUrl = "https://pay.payphone.com.ec/pay?id=tx_" + shortId;
        }

        log.info("Pedido registrado con éxito. ID: {}, Número: {}, Total: {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotal());

        return OrderCreateResponse.builder()
                .id(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .subtotal(savedOrder.getSubtotal())
                .shippingCost(savedOrder.getShippingCost())
                .total(savedOrder.getTotal())
                .status(savedOrder.getStatus().name())
                .paymentMethod(paymentMethod == PaymentMethod.payphone ? "payphone" : "transferencia")
                .payphoneUrl(payphoneUrl)
                .expiresAt(savedOrder.getExpiresAt())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderCreateResponse getOrderByOrderNumber(Long orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con número: " + orderNumber));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderCreateResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));

        return toResponse(order);
    }

    private OrderCreateResponse toResponse(Order order) {
        String payphoneUrl = null;
        if (order.getPaymentMethod() == PaymentMethod.payphone) {
            String shortId = order.getId().toString().replace("-", "").substring(0, 10);
            payphoneUrl = "https://pay.payphone.com.ec/pay?id=tx_" + shortId;
        }

        return OrderCreateResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .subtotal(order.getSubtotal())
                .shippingCost(order.getShippingCost())
                .total(order.getTotal())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod() == PaymentMethod.payphone ? "payphone" : "transferencia")
                .payphoneUrl(payphoneUrl)
                .expiresAt(order.getExpiresAt())
                .build();
    }

    /**
     * Confirma el pago de una orden (vía webhook de pasarela o conciliación bancaria).
     * Transiciona la orden a 'paid', el pago a 'approved' y las reservas de inventario a 'consumed'.
     */
    @Transactional
    public Order confirmOrderPayment(UUID orderId, String providerTransactionId, java.util.Map<String, Object> providerPayload) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() == OrderStatus.paid || order.getStatus() == OrderStatus.completed) {
            log.info("El pedido {} ya se encuentra confirmado/pagado. Idempotencia aplicada.", orderId);
            return order;
        }

        order.setStatus(OrderStatus.paid);
        order.setPaymentStatus(PaymentStatus.approved);

        // Actualizar registro de pago
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        if (!payments.isEmpty()) {
            Payment payment = payments.get(0);
            payment.setStatus(PaymentStatus.approved);
            if (providerTransactionId != null) {
                payment.setProviderTransactionId(providerTransactionId);
            }
            if (providerPayload != null) {
                payment.setProviderPayload(providerPayload);
            }
            paymentRepository.save(payment);
        }

        // Consolidar reservas de inventario
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(orderId);
        for (InventoryReservation res : reservations) {
            if (res.getStatus() == ReservationStatus.active) {
                res.setStatus(ReservationStatus.consumed);
            }
        }
        inventoryReservationRepository.saveAll(reservations);

        Order saved = orderRepository.saveAndFlush(order);
        log.info("Pedido {} confirmado exitosamente tras verificación de pago.", orderId);
        return saved;
    }

    /**
     * Cancela o revierte una orden por fallo en pasarela, rechazo o expiración.
     * Transiciona la orden a 'cancelled', el pago a 'rejected', las reservas a 'released'
     * y REVIERTE físicamente el stock de cada variante reservada.
     */
    @Transactional
    public Order cancelAndRevertOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId));

        if (order.getStatus() == OrderStatus.cancelled) {
            log.info("El pedido {} ya se encuentra cancelado. Idempotencia aplicada.", orderId);
            return order;
        }

        order.setStatus(OrderStatus.cancelled);
        order.setPaymentStatus(PaymentStatus.rejected);
        if (reason != null && !reason.isBlank()) {
            String note = order.getAdministrativeNotes() != null
                    ? order.getAdministrativeNotes() + " | Cancelación: " + reason
                    : "Cancelación: " + reason;
            order.setAdministrativeNotes(note);
        }

        // Actualizar pagos asociados
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.pending) {
                payment.setStatus(PaymentStatus.rejected);
            }
        }
        paymentRepository.saveAll(payments);

        // Liberar reservas de inventario y RESTITUIR stock físico
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(orderId);
        for (InventoryReservation res : reservations) {
            if (res.getStatus() == ReservationStatus.active) {
                res.setStatus(ReservationStatus.released);
                ProductVariant variant = res.getProductVariant();
                if (variant != null) {
                    variant.addStock(res.getQuantity());
                    productVariantRepository.save(variant);
                    log.info("Stock revertido: +{} para variante SKU: {}", res.getQuantity(), variant.getSku());
                }
            }
        }
        inventoryReservationRepository.saveAll(reservations);

        Order saved = orderRepository.saveAndFlush(order);
        log.info("Pedido {} cancelado exitosamente y stock restituido.", orderId);
        return saved;
    }

    private DeliveryMethod parseDeliveryMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new BadRequestException("El método de entrega es requerido ('delivery' o 'pickup')");
        }
        String clean = method.trim().toLowerCase();
        return switch (clean) {
            case "delivery" -> DeliveryMethod.delivery;
            case "pickup" -> DeliveryMethod.pickup;
            default -> throw new BadRequestException("Método de entrega desconocido: '" + method + "'. Valores permitidos: 'delivery', 'pickup'");
        };
    }

    private PaymentMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new BadRequestException("El método de pago es requerido ('payphone' o 'transferencia')");
        }
        String clean = method.trim().toLowerCase();
        return switch (clean) {
            case "payphone" -> PaymentMethod.payphone;
            case "transferencia", "transfer", "bank_transfer" -> PaymentMethod.bank_transfer;
            default -> throw new BadRequestException("Método de pago desconocido: '" + method + "'. Valores permitidos: 'payphone', 'transferencia'");
        };
    }
}
