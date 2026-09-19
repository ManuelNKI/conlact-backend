package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.association.AssociationResponse;
import com.conlact.conlact_backend.service.AssociationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/associations")
@RequiredArgsConstructor
public class AssociationController {

    private final AssociationService associationService;

    @GetMapping
    public ResponseEntity<List<AssociationResponse>> getAssociations() {
        return ResponseEntity.ok(
                associationService.getPublishedAssociations()
        );
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<AssociationResponse> getAssociation(
            @PathVariable String identifier) {

        return ResponseEntity.ok(
                associationService.getPublishedAssociationByIdOrSlug(identifier)
        );
    }
}
