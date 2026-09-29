package com.conlact.conlact_backend.dto.association.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationGalleryResponse {

    @JsonProperty("association_id")
    private UUID associationId;

    private List<AssociationImageItemResponse> images;
}
