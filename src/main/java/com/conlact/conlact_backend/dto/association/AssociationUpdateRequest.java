package com.conlact.conlact_backend.dto.association;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationUpdateRequest {

    @NotBlank(message = "El nombre de la filial es obligatorio")
    @Size(max = 255, message = "El nombre no puede exceder 255 caracteres")
    @JsonProperty("nombre")
    private String name;

    @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo puede contener letras minúsculas, números y guiones")
    @Size(max = 255, message = "El slug no puede exceder 255 caracteres")
    @JsonProperty("slug")
    private String slug;

    @Size(max = 500, message = "La descripción corta no puede exceder 500 caracteres")
    @JsonProperty("descripcion_corta")
    private String shortDescription;

    @JsonProperty("historia")
    private String history;

    @Size(max = 255, message = "La ubicación no puede exceder 255 caracteres")
    @JsonProperty("ubicacion")
    private String locationText;

    @JsonProperty("ubicacion_referencia")
    private String referenceLocation;

    @JsonProperty("fotos")
    private List<String> photos;

    @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
    @DecimalMax(value = "90.0", message = "La latitud debe ser menor o igual a 90")
    @JsonProperty("lat")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
    @DecimalMax(value = "180.0", message = "La longitud debe ser menor o igual a 180")
    @JsonProperty("lng")
    private BigDecimal longitude;

    @Size(max = 100, message = "El registro ARCSA no puede exceder 100 caracteres")
    @JsonProperty("registro_arcsa")
    private String arcsaRegistration;

    @JsonProperty("sello_arcsa")
    private String arcsaSeal;

    @Size(max = 100, message = "El registro AGROCALIDAD no puede exceder 100 caracteres")
    @JsonProperty("registro_agrocalidad")
    private String agrocalidadRegistration;

    @Size(max = 255, message = "El texto de sello sanitario no puede exceder 255 caracteres")
    @JsonProperty("sello_sanitario")
    private String sanitarySealText;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("instagram_url")
    private String instagramUrl;

    @JsonProperty("tiktok_url")
    private String tiktokUrl;

    @JsonProperty("facebook_url")
    private String facebookUrl;

    @Size(max = 50, message = "El número de WhatsApp no puede exceder 50 caracteres")
    @JsonProperty("whatsapp")
    private String whatsapp;

    @JsonProperty("is_published")
    private Boolean isPublished;

    public String getArcsaRegistration() {
        return (arcsaRegistration != null && !arcsaRegistration.isBlank()) ? arcsaRegistration : arcsaSeal;
    }

    public String getLocationText() {
        return (locationText != null && !locationText.isBlank()) ? locationText : referenceLocation;
    }
}
