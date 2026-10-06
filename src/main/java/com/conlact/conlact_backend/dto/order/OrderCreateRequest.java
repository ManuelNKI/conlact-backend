package com.conlact.conlact_backend.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreateRequest(
        @NotNull(message = "Los datos del cliente son requeridos")
        @Valid
        @JsonProperty("cliente")
        CustomerDto customer,

        @NotBlank(message = "El método de entrega es requerido ('delivery' o 'pickup')")
        @JsonProperty("metodo_entrega")
        String deliveryMethod,

        @JsonProperty("zona_envio_id")
        UUID shippingZoneId,

        @JsonProperty("direccion_entrega")
        String shippingAddress,

        @NotBlank(message = "El método de pago es requerido ('payphone' o 'transferencia')")
        @JsonProperty("metodo_pago")
        String paymentMethod,

        @JsonProperty("notas_cliente")
        String clientNotes,

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        @Valid
        @JsonProperty("items")
        List<OrderItemRequest> items
) {
    @Builder
    public record CustomerDto(
            @NotBlank(message = "El nombre del cliente es obligatorio")
            @JsonProperty("nombre")
            String name,

            @NotBlank(message = "La cédula o RUC es obligatoria")
            @JsonProperty("cedula_ruc")
            String taxId,

            @NotBlank(message = "El teléfono de contacto es obligatorio")
            @JsonProperty("telefono")
            String phone,

            @JsonProperty("email")
            String email,

            @JsonProperty("direccion")
            String address
    ) {}

    @Builder
    public record OrderItemRequest(
            @JsonProperty("producto_id")
            String productId,

            @NotNull(message = "El ID de la variante es obligatorio")
            @JsonProperty("variante_id")
            UUID variantId,

            @NotNull(message = "La cantidad es requerida")
            @Min(value = 1, message = "La cantidad mínima debe ser 1")
            @JsonProperty("cantidad")
            Integer quantity,

            @JsonProperty("precio_unitario")
            BigDecimal unitPrice
    ) {}
}
