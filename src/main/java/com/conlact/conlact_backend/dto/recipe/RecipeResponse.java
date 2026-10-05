package com.conlact.conlact_backend.dto.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RecipeResponse {

    private UUID id;

    @JsonProperty("titulo")
    private String title;

    private String slug;

    @JsonProperty("tipo_queso")
    private String cheeseType;

    @JsonProperty("tiempo_prep")
    private Integer preparationTime;

    @JsonProperty("porciones")
    private Integer servings;

    @JsonProperty("producto_slug")
    private String productSlug;

    @JsonProperty("imagen_url")
    private String imageUrl;

    private List<String> pasos;

    private List<String> ingredientes;

    @JsonProperty("productos_relacionados")
    private List<RelatedProductResponse> relatedProducts;
}