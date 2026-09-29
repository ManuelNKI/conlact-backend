package com.conlact.conlact_backend.dto.association.image;

import com.conlact.conlact_backend.entity.enums.AssociationImageType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationImageItemResponse {

    private UUID id;

    @JsonProperty("image_type")
    private AssociationImageType imageType;

    private String url;

    @JsonProperty("alt_text")
    private String altText;

    @JsonProperty("sort_order")
    private Integer sortOrder;
}
