package com.conlact.conlact_backend.dto.product;

import com.conlact.conlact_backend.entity.ProductVariant;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductPublicResponse(
        UUID id,
        @JsonProperty("nombre") String name,
        String slug,
        @JsonProperty("tipo_queso") String cheeseType,
        @JsonProperty("peso") String weight,
        @JsonProperty("precio") BigDecimal price,
        @JsonProperty("descripcion_corta") String shortDescription,
        @JsonProperty("descripcion") String description,
        @JsonProperty("origen") String originText,
        @JsonProperty("conservacion") String conservation,
        @JsonProperty("fotos") List<String> photos,
        @JsonProperty("asociacion") String associationName,
        @JsonProperty("asociacion_id") UUID associationId,
        @JsonProperty("categoria") String categoryName,
        @JsonProperty("categoria_id") UUID categoryId,
        long stock,
        @JsonProperty("disponible") boolean available,
        @JsonProperty("presentaciones") List<PresentationResponse> presentations
) {
    @Builder
    public record PresentationResponse(
            UUID id,
            String sku,
            @JsonProperty("nombre_presentacion") String presentationName,
            @JsonProperty("peso_gramos") Integer weightGrams,
            @JsonProperty("precio") BigDecimal price,
            Integer stock,
            @JsonProperty("is_low_stock") boolean lowStock
    ) {
        public static PresentationResponse fromEntity(ProductVariant variant) {
            return PresentationResponse.builder()
                    .id(variant.getId())
                    .sku(variant.getSku())
                    .presentationName(variant.getPresentationName())
                    .weightGrams(variant.getWeightGrams())
                    .price(variant.getPrice())
                    .stock(variant.getStock())
                    .lowStock(variant.isLowStock())
                    .build();
        }
    }
}
