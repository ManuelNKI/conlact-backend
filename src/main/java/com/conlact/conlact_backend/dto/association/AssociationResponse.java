package com.conlact.conlact_backend.dto.association;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssociationResponse {

    private UUID id;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("nombre")
    private String name;

    @JsonProperty("descripcion_corta")
    private String shortDescription;

    @JsonProperty("historia")
    private String history;

    @JsonProperty("ubicacion")
    private String locationText;

    @JsonProperty("ubicacion_referencia")
    private String referenceLocation;

    @JsonProperty("fotos")
    @Builder.Default
    private List<String> photos = Collections.emptyList();

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("sello_sanitario")
    private String sanitarySeal;

    @JsonProperty("registro_arcsa")
    private String arcsaRegistration;

    @JsonProperty("sello_arcsa")
    private String arcsaSeal;

    @JsonProperty("registro_agrocalidad")
    private String agrocalidadRegistration;

    @JsonProperty("registro_bpm")
    private String bpmRegistration;

    @JsonProperty("lat")
    private BigDecimal latitude;

    @JsonProperty("lng")
    private BigDecimal longitude;

    @JsonProperty("whatsapp")
    private String whatsapp;

    @JsonProperty("contacto_asociacion")
    private String associationContact;

    @JsonProperty("instagram_url")
    private String instagramUrl;

    @JsonProperty("tiktok_url")
    private String tiktokUrl;

    @JsonProperty("facebook_url")
    private String facebookUrl;

    @JsonProperty("redes_sociales")
    private SocialNetworksDto socialNetworks;

    @JsonProperty("numero_familias")
    private Integer numberOfFamilies;

    @JsonProperty("altitud_msnm")
    private Integer altitudeMsnm;

    @JsonProperty("horario_atencion")
    private String businessHours;

    @JsonProperty("productos_ids")
    @Builder.Default
    private List<String> productIds = Collections.emptyList();

    @JsonProperty("is_published")
    private Boolean isPublished;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SocialNetworksDto {
        @JsonProperty("facebook")
        private String facebook;

        @JsonProperty("instagram")
        private String instagram;

        @JsonProperty("tiktok")
        private String tiktok;

        @JsonProperty("whatsapp")
        private String whatsapp;
    }
}
