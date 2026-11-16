package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.training.TrainingSectionResponse;
import com.conlact.conlact_backend.service.TrainingContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/capacitacion")
@RequiredArgsConstructor
public class TrainingContentController {

    private final TrainingContentService trainingContentService;

    @GetMapping
    public ResponseEntity<List<TrainingSectionResponse>> getPublicTraining() {
        return ResponseEntity.ok(trainingContentService.getPublicTraining());
    }
}
