package com.conlact.conlact_backend.dto.variant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantCreateRequest {

    @NotBlank(message = "El SKU es obligatorio")
    @Size(max = 50, message = "El SKU no puede exceder los 50 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El SKU solo debe contener letras, números, guiones y guiones bajos")
    private String sku;

    @NotBlank(message = "El nombre de la presentación es obligatorio (ej. Cuña 250g, Bloque 500g, Rueda 1kg)")
    @Size(max = 100, message = "El nombre de la presentación no puede exceder los 100 caracteres")
    @JsonProperty("presentation_name")
    private String presentationName;

    @Min(value = 1, message = "El peso en gramos debe ser mayor a 0 si se especifica")
    @JsonProperty("weight_grams")
    private Integer weightGrams;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal price;

    @NotNull(message = "El stock inicial es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    @Builder.Default
    private Integer stock = 0;

    @Min(value = 0, message = "El umbral de stock bajo no puede ser negativo")
    @JsonProperty("low_stock_threshold")
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @JsonProperty("is_active")
    @Builder.Default
    private Boolean isActive = true;
}
