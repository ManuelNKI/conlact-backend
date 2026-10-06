package com.conlact.conlact_backend.dto.association.image;

import com.conlact.conlact_backend.entity.enums.AssociationImageType;
import com.conlact.conlact_backend.validation.ValidAssociationImageUrl;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssociationImageRequest {

    @Size(max = 2048, message = "La URL no puede superar 2048 caracteres")
    @ValidAssociationImageUrl
    private String url;

    @JsonProperty("image_type")
    private AssociationImageType imageType;

    @Size(max = 255, message = "El texto alternativo no puede superar 255 caracteres")
    @JsonProperty("alt_text")
    private String altText;

    @Min(value = 0, message = "El orden de visualización no puede ser negativo")
    @JsonProperty("sort_order")
    private Integer sortOrder;
}
