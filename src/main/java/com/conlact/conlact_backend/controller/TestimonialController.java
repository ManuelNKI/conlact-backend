package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.testimonial.TestimonialResponse;
import com.conlact.conlact_backend.service.TestimonialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping({"/api/testimonios", "/api/testimonials"})
@RequiredArgsConstructor
public class TestimonialController {
    private final TestimonialService testimonialService;

    @GetMapping
    public ResponseEntity<List<TestimonialResponse>> getTestimonials(
            @RequestParam(name = "destacados", defaultValue = "false") boolean featuredOnly) {
        return ResponseEntity.ok(testimonialService.getPublicTestimonials(featuredOnly));
    }
}
