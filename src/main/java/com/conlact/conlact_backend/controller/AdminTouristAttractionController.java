package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionRequest;
import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.service.TouristAttractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/turismo", "/api/admin/tourism"})
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@RequiredArgsConstructor
public class AdminTouristAttractionController {

    private final TouristAttractionService touristAttractionService;

    @GetMapping
    public ResponseEntity<List<TouristAttractionResponse>> getAllAttractions() {
        return ResponseEntity.ok(touristAttractionService.getAllAttractions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TouristAttractionResponse> getAttractionById(@PathVariable UUID id) {
        return ResponseEntity.ok(touristAttractionService.getAttractionById(id));
    }

    @PostMapping
    public ResponseEntity<TouristAttractionResponse> createAttraction(
            @Valid @RequestBody TouristAttractionRequest request) {
        TouristAttractionResponse response = touristAttractionService.createAttraction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TouristAttractionResponse> updateAttraction(
            @PathVariable UUID id,
            @Valid @RequestBody TouristAttractionRequest request) {
        TouristAttractionResponse response = touristAttractionService.updateAttraction(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttraction(@PathVariable UUID id) {
        touristAttractionService.deleteAttraction(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping({"/{id}/publicacion", "/{id}/publish"})
    public ResponseEntity<TouristAttractionResponse> changePublication(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Boolean published = (body != null && body.containsKey("publicado")) ? body.get("publicado") : null;
        return ResponseEntity.ok(touristAttractionService.changePublication(id, published));
    }
}
