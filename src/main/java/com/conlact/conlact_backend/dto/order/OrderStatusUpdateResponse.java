package com.conlact.conlact_backend.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderStatusUpdateResponse(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("numero_pedido")
        Long orderNumber,

        @JsonProperty("estado_anterior")
        String previousStatus,

        @JsonProperty("estado_actual")
        String currentStatus,

        @JsonProperty("notas_administrativas")
        String administrativeNotes,

        @JsonProperty("updated_at")
        OffsetDateTime updatedAt
) {
}
