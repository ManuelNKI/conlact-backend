package com.conlact.conlact_backend.dto.variant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantUpdateRequest {

    @Size(max = 50, message = "El SKU no puede exceder los 50 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El SKU solo debe contener letras, números, guiones y guiones bajos")
    private String sku;

    @Size(max = 100, message = "El nombre de la presentación no puede exceder los 100 caracteres")
    @JsonProperty("presentation_name")
    private String presentationName;

    @Min(value = 1, message = "El peso en gramos debe ser mayor a 0 si se especifica")
    @JsonProperty("weight_grams")
    private Integer weightGrams;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal price;

    @Min(value = 0, message = "El umbral de stock bajo no puede ser negativo")
    @JsonProperty("low_stock_threshold")
    private Integer lowStockThreshold;

    @JsonProperty("is_active")
    private Boolean isActive;
}
