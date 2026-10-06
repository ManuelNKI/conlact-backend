package com.conlact.conlact_backend.dto.variant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationRequest {

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer quantity;

    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;

    @Min(value = 0, message = "La versión no puede ser negativa")
    private Long version;
}
