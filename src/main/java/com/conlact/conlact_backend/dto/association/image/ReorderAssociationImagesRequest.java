package com.conlact.conlact_backend.dto.association.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderAssociationImagesRequest {

    @NotEmpty(message = "La lista de imágenes a reordenar no puede estar vacía")
    private List<@Valid ImageOrderItemRequest> images;
}
