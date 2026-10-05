package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RecommendedProductResponse {

    private UUID id;

    private String slug;

    @JsonProperty("nombre")
    private String name;

    @JsonProperty("tipo_queso")
    private String cheeseType;
}