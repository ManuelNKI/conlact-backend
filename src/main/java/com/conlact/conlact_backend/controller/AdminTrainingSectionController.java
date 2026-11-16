package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.training.TrainingSectionCreateRequest;
import com.conlact.conlact_backend.dto.training.TrainingSectionPatchRequest;
import com.conlact.conlact_backend.dto.training.TrainingSectionResponse;
import com.conlact.conlact_backend.service.TrainingContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/secciones")
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@RequiredArgsConstructor
public class AdminTrainingSectionController {

    private final TrainingContentService trainingContentService;

    @GetMapping
    public ResponseEntity<List<TrainingSectionResponse>> getAllSections() {
        return ResponseEntity.ok(trainingContentService.getAllSections());
    }

    @PostMapping
    public ResponseEntity<TrainingSectionResponse> createSection(
            @Valid @RequestBody TrainingSectionCreateRequest request) {
        TrainingSectionResponse created = trainingContentService.createSection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TrainingSectionResponse> patchSection(
            @PathVariable UUID id,
            @Valid @RequestBody TrainingSectionPatchRequest request) {
        TrainingSectionResponse updated = trainingContentService.patchSection(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable UUID id) {
        trainingContentService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }
}
