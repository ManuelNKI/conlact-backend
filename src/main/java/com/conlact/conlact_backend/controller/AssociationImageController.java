package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.image.*;
import com.conlact.conlact_backend.service.AssociationImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/associations/{associationId}/fotos")
@RequiredArgsConstructor
public class AssociationImageController {

    private final AssociationImageService associationImageService;

    /**
     * GET /api/asociaciones/{associationId}/fotos
     * Consulta pública de fotos para asociaciones publicadas.
     */
    @GetMapping
    public ResponseEntity<AssociationGalleryResponse> getGallery(
            @PathVariable UUID associationId
    ) {
        return ResponseEntity.ok(associationImageService.getPublishedAssociationImages(associationId));
    }

    /**
     * POST /api/asociaciones/{associationId}/fotos
     * Registra una nueva URL de foto para una asociación (Requiere rol ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssociationImageItemResponse> createImage(
            @PathVariable UUID associationId,
            @Valid @RequestBody CreateAssociationImageRequest request
    ) {
        AssociationImageItemResponse created = associationImageService.createAssociationImage(associationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PATCH /api/asociaciones/{associationId}/fotos/{imageId}
     * Actualiza metadatos, URL, tipo u orden de una fotografía (Requiere rol ADMIN).
     */
    @PatchMapping("/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssociationImageItemResponse> updateImage(
            @PathVariable UUID associationId,
            @PathVariable UUID imageId,
            @Valid @RequestBody UpdateAssociationImageRequest request
    ) {
        AssociationImageItemResponse updated = associationImageService.updateAssociationImage(associationId, imageId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/asociaciones/{associationId}/fotos/{imageId}
     * Desvincula una fotografía de la asociación (Requiere rol ADMIN).
     */
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID associationId,
            @PathVariable UUID imageId
    ) {
        associationImageService.deleteAssociationImage(associationId, imageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/asociaciones/{associationId}/fotos/orden
     * Reordena transaccionalmente varias fotografías de la galería (Requiere rol ADMIN).
     */
    @PatchMapping("/orden")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssociationGalleryResponse> reorderImages(
            @PathVariable UUID associationId,
            @Valid @RequestBody ReorderAssociationImagesRequest request
    ) {
        AssociationGalleryResponse reordered = associationImageService.reorderAssociationImages(associationId, request);
        return ResponseEntity.ok(reordered);
    }
}
