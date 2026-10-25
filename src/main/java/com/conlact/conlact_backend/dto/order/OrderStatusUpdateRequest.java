package com.conlact.conlact_backend.dto.order;

import com.conlact.conlact_backend.entity.enums.OrderStatus;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record OrderStatusUpdateRequest(
        @NotBlank(message = "El nuevo estado es obligatorio")
        @JsonProperty("nuevo_estado")
        @JsonAlias({"status", "new_status", "estado"})
        String newStatus,

        @JsonProperty("notas_administrativas")
        @JsonAlias({"administrative_notes", "notes", "notas"})
        String administrativeNotes
) {

    /**
     * Parsea e interpreta de forma tolerante el estado entrante en español o inglés.
     */
    public static OrderStatus parseStatus(String input) {
        if (input == null || input.isBlank()) {
            throw new BadRequestException("El nuevo estado es obligatorio");
        }
        String clean = input.trim().toLowerCase().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "pending", "pendiente" -> OrderStatus.pending;
            case "paid", "pagado" -> OrderStatus.paid;
            case "preparing", "en_preparacion", "en_preparación", "preparando", "preparacion" -> OrderStatus.preparing;
            case "shipped", "enviado" -> OrderStatus.shipped;
            case "completed", "entregado", "completado" -> OrderStatus.completed;
            case "cancelled", "canceled", "cancelado" -> OrderStatus.cancelled;
            default -> throw new BadRequestException(
                    "Estado de pedido no válido: '" + input + "'. Estados válidos: pending (pendiente), paid (pagado), preparing (en_preparacion), shipped (enviado), completed (entregado), cancelled (cancelado)"
            );
        };
    }
}
