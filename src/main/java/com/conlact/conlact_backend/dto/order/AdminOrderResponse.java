package com.conlact.conlact_backend.dto.order;
import com.conlact.conlact_backend.entity.Order;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record AdminOrderResponse(UUID id, Long numero_pedido, String estado, String cliente,
    String telefono, String email, String cedula_ruc, String entrega, String direccion,
    BigDecimal subtotal, BigDecimal flete, BigDecimal total, String notas_administrativas,
    OffsetDateTime fecha, List<Item> items) {
    public record Item(String nombre, String presentacion, Integer cantidad, BigDecimal precio) {}
    public static AdminOrderResponse from(Order o) {
        return new AdminOrderResponse(o.getId(),o.getOrderNumber(),o.getStatus().name(),o.getCustomerName(),
            o.getCustomerPhone(),o.getCustomerEmail(),o.getCustomerTaxId(),o.getDeliveryMethod().name(),o.getShippingAddress(),
            o.getSubtotal(),o.getShippingCost(),o.getTotal(),o.getAdministrativeNotes(),o.getCreatedAt(),
            o.getItems().stream().map(i -> new Item(i.getProductNameSnapshot(),i.getPresentationSnapshot(),i.getQuantity(),i.getUnitPrice())).toList());
    }
}
