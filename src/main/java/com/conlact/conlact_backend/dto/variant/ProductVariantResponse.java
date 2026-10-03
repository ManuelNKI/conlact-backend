package com.conlact.conlact_backend.dto.variant;

import com.conlact.conlact_backend.entity.ProductVariant;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {

    private UUID id;

    @JsonProperty("product_id")
    private UUID productId;

    private String sku;

    @JsonProperty("presentation_name")
    private String presentationName;

    @JsonProperty("weight_grams")
    private Integer weightGrams;

    private BigDecimal price;

    private Integer stock;

    @JsonProperty("low_stock_threshold")
    private Integer lowStockThreshold;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("is_low_stock")
    private Boolean isLowStock;

    private Long version;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    public static ProductVariantResponse fromEntity(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct() != null ? variant.getProduct().getId() : null)
                .sku(variant.getSku())
                .presentationName(variant.getPresentationName())
                .weightGrams(variant.getWeightGrams())
                .price(variant.getPrice())
                .stock(variant.getStock())
                .lowStockThreshold(variant.getLowStockThreshold())
                .isActive(variant.getIsActive())
                .isLowStock(variant.isLowStock())
                .version(variant.getVersion())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }
}
