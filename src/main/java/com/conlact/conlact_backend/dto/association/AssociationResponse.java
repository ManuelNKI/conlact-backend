package com.conlact.conlact_backend.dto.association;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
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

    @JsonProperty("fotos")
    private List<String> photos;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("sello_sanitario")
    private String sanitarySeal;

    @JsonProperty("registro_arcsa")
    private String arcsaRegistration;

    @JsonProperty("registro_agrocalidad")
    private String agrocalidadRegistration;

    @JsonProperty("lat")
    private BigDecimal latitude;

    @JsonProperty("lng")
    private BigDecimal longitude;

    @JsonProperty("whatsapp")
    private String whatsapp;

    @JsonProperty("instagram_url")
    private String instagramUrl;

    @JsonProperty("tiktok_url")
    private String tiktokUrl;

    @JsonProperty("facebook_url")
    private String facebookUrl;
}
