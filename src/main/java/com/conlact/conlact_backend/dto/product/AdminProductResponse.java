package com.conlact.conlact_backend.dto.product;

import com.conlact.conlact_backend.entity.ProductVariant;
import com.conlact.conlact_backend.entity.enums.ProductStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminProductResponse(
        UUID id,
        @JsonProperty("nombre") String name,
        String slug,
        @JsonProperty("tipo_queso") String cheeseType,
        @JsonProperty("descripcion_corta") String shortDescription,
        @JsonProperty("descripcion") String description,
        @JsonProperty("origen") String originText,
        @JsonProperty("conservacion") String conservation,
        @JsonProperty("asociacion") String associationName,
        @JsonProperty("asociacion_id") UUID associationId,
        @JsonProperty("categoria") String categoryName,
        @JsonProperty("categoria_id") UUID categoryId,
        ProductStatus status,
        @JsonProperty("is_featured") Boolean isFeatured,
        @JsonProperty("peso") String weight,
        @JsonProperty("precio") BigDecimal price,
        long stock,
        @JsonProperty("disponible") boolean available,
        @JsonProperty("fotos") List<String> photos,
        @JsonProperty("presentaciones") List<PresentationResponse> variants,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
    public record PresentationResponse(
            UUID id,
            String sku,
            @JsonProperty("nombre_presentacion") String presentationName,
            @JsonProperty("peso_gramos") Integer weightGrams,
            @JsonProperty("precio") BigDecimal price,
            Integer stock,
            @JsonProperty("low_stock_threshold") Integer lowStockThreshold,
            @JsonProperty("is_low_stock") boolean lowStock,
            @JsonProperty("is_active") Boolean isActive,
            Long version
    ) {
        public static PresentationResponse fromEntity(ProductVariant variant) {
            return new PresentationResponse(variant.getId(), variant.getSku(), variant.getPresentationName(),
                    variant.getWeightGrams(), variant.getPrice(), variant.getStock(), variant.getLowStockThreshold(),
                    variant.isLowStock(), variant.getIsActive(), variant.getVersion());
        }
    }
}
