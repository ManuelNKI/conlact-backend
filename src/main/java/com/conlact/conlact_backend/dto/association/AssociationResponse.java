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

    @JsonProperty("nombre")
    private String name;

    @JsonProperty("historia")
    private String history;

    @JsonProperty("fotos")
    private List<String> photos;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("sello_sanitario")
    private String sanitarySeal;

    @JsonProperty("lat")
    private BigDecimal latitude;

    @JsonProperty("lng")
    private BigDecimal longitude;
}