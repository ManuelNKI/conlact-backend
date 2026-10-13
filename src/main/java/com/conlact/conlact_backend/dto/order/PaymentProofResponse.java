package com.conlact.conlact_backend.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentProofResponse(
        @JsonProperty("pedido_id")
        UUID orderId,
        @JsonProperty("pago_id")
        UUID paymentId,
        @JsonProperty("metodo_pago")
        String paymentMethod,
        @JsonProperty("estado_pago")
        String paymentStatus,
        @JsonProperty("ruta_comprobante")
        String proofStoragePath,
        @JsonProperty("referencia_bancaria")
        String bankReference,
        @JsonProperty("registrado_en")
        OffsetDateTime registeredAt
) {}
