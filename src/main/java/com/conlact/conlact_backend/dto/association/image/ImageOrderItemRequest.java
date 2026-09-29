package com.conlact.conlact_backend.dto.association.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageOrderItemRequest {

    @NotNull(message = "El id de la fotografía es obligatorio")
    private UUID id;

    @NotNull(message = "El orden es obligatorio")
    @Min(value = 0, message = "El orden no puede ser negativo")
    @JsonProperty("sort_order")
    private Integer sortOrder;
}
