package com.conlact.conlact_backend.dto.product.image;

import com.conlact.conlact_backend.entity.ProductImage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductImageResponse(
        UUID id,
        @JsonProperty("product_id")
        UUID productId,
        @JsonProperty("storage_path")
        String storagePath,
        String url,
        @JsonProperty("alt_text")
        String altText,
        @JsonProperty("sort_order")
        Integer sortOrder,
        @JsonProperty("is_primary")
        boolean isPrimary,
        @JsonProperty("created_at")
        OffsetDateTime createdAt
) {
    public static ProductImageResponse fromEntity(ProductImage image, String publicUrl) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .productId(image.getProduct() != null ? image.getProduct().getId() : null)
                .storagePath(image.getStoragePath())
                .url(publicUrl)
                .altText(image.getAltText())
                .sortOrder(image.getSortOrder())
                .isPrimary(image.isPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
