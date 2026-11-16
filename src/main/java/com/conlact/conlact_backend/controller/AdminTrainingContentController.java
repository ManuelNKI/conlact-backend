package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.training.TrainingContentCreateRequest;
import com.conlact.conlact_backend.dto.training.TrainingContentPatchRequest;
import com.conlact.conlact_backend.dto.training.TrainingContentResponse;
import com.conlact.conlact_backend.service.TrainingContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/contenidos")
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@RequiredArgsConstructor
public class AdminTrainingContentController {

    private final TrainingContentService trainingContentService;

    @PostMapping
    public ResponseEntity<TrainingContentResponse> createContent(
            @Valid @RequestBody TrainingContentCreateRequest request) {
        TrainingContentResponse created = trainingContentService.createContent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TrainingContentResponse> patchContent(
            @PathVariable UUID id,
            @Valid @RequestBody TrainingContentPatchRequest request) {
        TrainingContentResponse updated = trainingContentService.patchContent(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContent(@PathVariable UUID id) {
        trainingContentService.deleteContent(id);
        return ResponseEntity.noContent().build();
    }
}
