package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.tourism.TouristAttractionResponse;
import com.conlact.conlact_backend.service.TouristAttractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/turismo", "/api/tourism"})
@RequiredArgsConstructor
public class TouristAttractionController {

    private final TouristAttractionService touristAttractionService;

    @GetMapping
    public ResponseEntity<List<TouristAttractionResponse>> getAttractions(
            @RequestParam(name = "asociacion_id", required = false) UUID associationId) {

        List<TouristAttractionResponse> response = touristAttractionService.getPublishedAttractions(associationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TouristAttractionResponse> getAttractionById(@PathVariable UUID id) {
        TouristAttractionResponse response = touristAttractionService.getPublishedAttractionById(id);
        return ResponseEntity.ok(response);
    }
}
