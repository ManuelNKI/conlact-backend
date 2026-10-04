package com.conlact.conlact_backend.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record OrderCreateResponse(
        UUID id,
        @JsonProperty("numero_pedido")
        Long orderNumber,
        BigDecimal subtotal,
        @JsonProperty("flete")
        BigDecimal shippingCost,
        BigDecimal total,
        @JsonProperty("estado")
        String status,
        @JsonProperty("metodo_pago")
        String paymentMethod,
        @JsonProperty("payphone_url")
        String payphoneUrl,
        @JsonProperty("reserva_expira_en")
        OffsetDateTime expiresAt
) {}
