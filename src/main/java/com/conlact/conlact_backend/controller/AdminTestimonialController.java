package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.testimonial.AdminTestimonialResponse;
import com.conlact.conlact_backend.dto.testimonial.TestimonialPublicationRequest;
import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.service.TestimonialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/testimonios", "/api/admin/testimonials"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTestimonialController {
    private final TestimonialService testimonialService;

    @GetMapping
    public ResponseEntity<List<AdminTestimonialResponse>> getTestimonials() {
        return ResponseEntity.ok(testimonialService.getAllTestimonials());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminTestimonialResponse> getTestimonial(@PathVariable UUID id) {
        return ResponseEntity.ok(testimonialService.getTestimonialById(id));
    }

    @PostMapping
    public ResponseEntity<AdminTestimonialResponse> createTestimonial(@Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testimonialService.createTestimonial(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminTestimonialResponse> updateTestimonial(
            @PathVariable UUID id, @Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.ok(testimonialService.updateTestimonial(id, request));
    }

    @PatchMapping({"/{id}/publicar", "/{id}/publish"})
    public ResponseEntity<AdminTestimonialResponse> changePublication(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TestimonialPublicationRequest request) {
        return ResponseEntity.ok(testimonialService.changePublication(id, request == null ? null : request.isPublished()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestimonial(@PathVariable UUID id) {
        testimonialService.deleteTestimonial(id);
        return ResponseEntity.noContent().build();
    }
}
