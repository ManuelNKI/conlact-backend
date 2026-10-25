package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.order.OrderCreateRequest;
import com.conlact.conlact_backend.dto.order.OrderCreateResponse;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateRequest;
import com.conlact.conlact_backend.dto.order.OrderStatusUpdateResponse;
import com.conlact.conlact_backend.email.EmailTemplate;
import com.conlact.conlact_backend.email.IEmailService;
import com.conlact.conlact_backend.email.TemplatedEmail;
import com.conlact.conlact_backend.entity.*;
import com.conlact.conlact_backend.entity.enums.*;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.InsufficientStockException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.*;
import com.conlact.conlact_backend.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ShippingZoneRepository shippingZoneRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final IEmailService emailService;

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

            BigDecimal unitPrice = variant.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            subtotal = subtotal.add(itemSubtotal);

            // Preparar items y reservas para asociar a la orden
            OrderItem orderItem = OrderItem.builder()
                    .productVariant(variant)
                    .productNameSnapshot(product.getName())
                    .presentationSnapshot(variant.getPresentationName())
                    .skuSnapshot(variant.getSku())
                    .unitPrice(unitPrice)
                    .quantity(requestedQuantity)
                    .build();
            orderItems.add(orderItem);

            InventoryReservation reservation = InventoryReservation.builder()
                    .productVariant(variant)
                    .quantity(requestedQuantity)
                    .status(ReservationStatus.active)
                    .expiresAt(expiresAt)
                    .build();
            reservations.add(reservation);
        }

        // 4. Calcular total final
        BigDecimal total = subtotal.add(shippingCost);

        // 5. Construir y persistir la entidad Order
        Order order = Order.builder()
                .customerName(request.customer().name().trim())
                .customerTaxId(request.customer().taxId().trim())
                .customerPhone(request.customer().phone().trim())
                .customerEmail(request.customer().email() != null && !request.customer().email().isBlank()
                        ? request.customer().email().trim().toLowerCase() : null)
                .billingAddress(request.customer().address() != null ? request.customer().address().trim() : null)
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

        // Asociar relación bidireccional
        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        order.setItems(orderItems);

        // 6. Crear registro de pago pendiente asociado
        Payment payment = Payment.builder()
                .order(order)
                .method(paymentMethod)
                .amount(total)
                .status(PaymentStatus.pending)
                .build();
        order.setPayments(new ArrayList<>(List.of(payment)));

        // Guardar la orden y sus dependencias en cascada
        Order savedOrder = orderRepository.saveAndFlush(order);

        // Asociar orden persistida a las reservas de inventario y guardarlas
        for (InventoryReservation res : reservations) {
            res.setOrder(savedOrder);
        }
        inventoryReservationRepository.saveAll(reservations);

        // Refrescar para obtener orderNumber generado por sequence en base de datos si aplica
        if (savedOrder.getOrderNumber() == null && entityManager != null) {
            entityManager.refresh(savedOrder);
        }

        log.info("Pedido registrado con éxito. ID: {}, Número: {}, Total: {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotal());

        return toResponse(savedOrder);
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

    @Transactional(readOnly = true)
    public List<OrderCreateResponse> getAllOrders(OrderStatus status) {
        List<Order> orders = (status != null)
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status)
                : orderRepository.findAllByOrderByCreatedAtDesc();

        return orders.stream()
                .map(this::toResponse)
                .toList();
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
     * BE-33: Transición controlada de la máquina de estados de un pedido y registro
     * inmutable en la tabla audit_log con usuario, rol, timestamp, estado anterior y nuevo.
     */
    @Transactional
    public OrderStatusUpdateResponse updateOrderStatus(UUID id, OrderStatusUpdateRequest request, UserPrincipal principal) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus = OrderStatusUpdateRequest.parseStatus(request.newStatus());

        if (currentStatus == targetStatus) {
            throw new BadRequestException("El pedido ya se encuentra en el estado '" + targetStatus.name() + "'");
        }

        validateStateTransition(currentStatus, targetStatus);

        String previousStatusStr = currentStatus.name();
        String targetStatusStr = targetStatus.name();

        String notes = (request.administrativeNotes() != null && !request.administrativeNotes().isBlank())
                ? request.administrativeNotes().trim()
                : null;

        if (targetStatus == OrderStatus.cancelled) {
            // Cancelar y revertir reservas, pagos y devolver stock al inventario
            order = cancelAndRevertOrder(id, notes);
        } else {
            order.setStatus(targetStatus);
            if (notes != null) {
                String existingNotes = order.getAdministrativeNotes();
                order.setAdministrativeNotes(existingNotes != null && !existingNotes.isBlank()
                        ? existingNotes + " | " + notes
                        : notes);
            }

            if (targetStatus == OrderStatus.paid || targetStatus == OrderStatus.preparing) {
                handlePaymentAndReservationConsumption(order);
            }

            order = orderRepository.saveAndFlush(order);
        }

        // Auditoría obligatoria e inmutable en audit_log
        UUID actorId = principal != null ? principal.getId() : null;
        String actorEmail = principal != null ? principal.getEmail() : "admin@conlact.org";
        String actorRole = principal != null ? principal.getRole() : "admin";

        Map<String, Object> oldData = new LinkedHashMap<>();
        oldData.put("status", previousStatusStr);

        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("status", targetStatusStr);
        newData.put("usuario", actorEmail);
        newData.put("rol", actorRole);
        newData.put("timestamp", OffsetDateTime.now().toString());
        if (notes != null) {
            newData.put("notas_administrativas", notes);
        }

        AuditLog auditLog = AuditLog.builder()
                .actorUserId(actorId)
                .entityName("orders")
                .entityId(order.getId().toString())
                .action("ORDER_STATUS_UPDATE")
                .oldData(oldData)
                .newData(newData)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Auditoría BE-33 registrada exitosamente en audit_log para pedido {}: {} -> {} por {}",
                order.getId(), previousStatusStr, targetStatusStr, actorEmail);

        // Despacho opcional y asíncrono de correo si el cliente tiene email registrado
        sendStatusChangeEmailIfPossible(order, targetStatus);

        return OrderStatusUpdateResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .previousStatus(previousStatusStr)
                .currentStatus(targetStatusStr)
                .administrativeNotes(order.getAdministrativeNotes())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Valida que la transición respete la máquina de estados de negocio:
     * PENDIENTE -> PAGADO -> EN_PREPARACION -> ENVIADO -> ENTREGADO (o CANCELADO desde cualquier estado activo)
     */
    private void validateStateTransition(OrderStatus current, OrderStatus target) {
        boolean isValid = switch (current) {
            case pending -> (target == OrderStatus.paid || target == OrderStatus.preparing || target == OrderStatus.cancelled);
            case paid -> (target == OrderStatus.preparing || target == OrderStatus.cancelled);
            case preparing -> (target == OrderStatus.shipped || target == OrderStatus.cancelled);
            case shipped -> (target == OrderStatus.completed || target == OrderStatus.cancelled);
            case completed, cancelled -> false; // Estados terminales
        };

        if (!isValid) {
            throw new BadRequestException(String.format(
                    "Transición de estado no válida: no se puede cambiar de '%s' a '%s'. La secuencia válida es PENDIENTE -> PAGADO -> EN_PREPARACION -> ENVIADO -> ENTREGADO (o CANCELADO desde cualquier estado activo)",
                    current.name(), target.name()
            ));
        }
    }

    private void handlePaymentAndReservationConsumption(Order order) {
        order.setPaymentStatus(PaymentStatus.approved);

        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.pending) {
                payment.setStatus(PaymentStatus.approved);
            }
        }
        paymentRepository.saveAll(payments);

        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(order.getId());
        for (InventoryReservation res : reservations) {
            if (res.getStatus() == ReservationStatus.active) {
                res.setStatus(ReservationStatus.consumed);
            }
        }
        inventoryReservationRepository.saveAll(reservations);
    }

    private void sendStatusChangeEmailIfPossible(Order order, OrderStatus newStatus) {
        if (order.getCustomerEmail() == null || order.getCustomerEmail().isBlank() || emailService == null) {
            return;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("customerName", order.getCustomerName());
            variables.put("orderNumber", String.valueOf(order.getOrderNumber()));
            variables.put("status", newStatus.name());
            variables.put("message", "Tu pedido ha cambiado al estado: " + newStatus.name());

            emailService.sendEmail(new TemplatedEmail(
                    order.getCustomerEmail(),
                    "[CONLAC-T] Actualización de tu pedido #" + order.getOrderNumber() + ": " + newStatus.name(),
                    EmailTemplate.ORDER_STATUS,
                    variables
            ));
        } catch (Exception ex) {
            log.warn("No se pudo despachar el correo de actualización de estado para el pedido {}: {}", order.getId(), ex.getMessage());
        }
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

    /**
     * BE-30 / BE-31: Libera de forma transaccional todas las reservas de stock vencidas
     * (expiradas por superar el tiempo límite sin completar el pago), restituye el inventario
     * físico a cada variante y transiciona la orden asociada a cancelada.
     */
    @Transactional
    public int releaseExpiredReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<InventoryReservation> expired = inventoryReservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.active, now);

        if (expired.isEmpty()) {
            return 0;
        }

        log.info("Procesando expiración automática de {} reservas de inventario vencidas...", expired.size());
        for (InventoryReservation res : expired) {
            res.setStatus(ReservationStatus.released);
            ProductVariant variant = res.getProductVariant();
            if (variant != null) {
                variant.addStock(res.getQuantity());
                productVariantRepository.save(variant);
                log.info("Stock restituido por expiración: +{} para variante SKU: {}", res.getQuantity(), variant.getSku());
            }

            Order order = res.getOrder();
            if (order != null && order.getStatus() == OrderStatus.pending) {
                order.setStatus(OrderStatus.cancelled);
                order.setPaymentStatus(PaymentStatus.rejected);
                String note = order.getAdministrativeNotes() != null
                        ? order.getAdministrativeNotes() + " | Cancelado por expiración de reserva temporal"
                        : "Cancelado por expiración de reserva temporal";
                order.setAdministrativeNotes(note);
                orderRepository.save(order);
            }
        }
        inventoryReservationRepository.saveAll(expired);
        return expired.size();
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
