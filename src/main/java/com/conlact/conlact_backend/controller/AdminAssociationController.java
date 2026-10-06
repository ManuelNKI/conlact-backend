package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.AssociationCreateRequest;
import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.dto.association.AssociationUpdateRequest;
import com.conlact.conlact_backend.service.AssociationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/asociaciones", "/api/admin/associations"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAssociationController {

    private final AssociationService associationService;

    @GetMapping
    public ResponseEntity<List<AssociationResponse>> getAllAssociations() {
        return ResponseEntity.ok(associationService.getAllAssociations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssociationResponse> getAssociationById(@PathVariable UUID id) {
        return ResponseEntity.ok(associationService.getAssociationById(id));
    }

    @PostMapping
    public ResponseEntity<AssociationResponse> createAssociation(
            @Valid @RequestBody AssociationCreateRequest request) {

        AssociationResponse created = associationService.createAssociation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssociationResponse> updateAssociation(
            @PathVariable UUID id,
            @Valid @RequestBody AssociationUpdateRequest request) {

        AssociationResponse updated = associationService.updateAssociation(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteAssociation(@PathVariable UUID id) {
        associationService.softDeleteAssociation(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<AssociationResponse> restoreAssociation(@PathVariable UUID id) {
        AssociationResponse restored = associationService.restoreAssociation(id);
        return ResponseEntity.ok(restored);
    }
}
