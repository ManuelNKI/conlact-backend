package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class RelatedProductResponse {

    @JsonProperty("producto_id")
    private UUID productId;

    private String nombre;

    private BigDecimal precio;

    @JsonProperty("es_recomendado")
    private Boolean recommended;
}