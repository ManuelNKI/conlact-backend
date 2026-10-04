package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.testimonial.AdminTestimonialResponse;
import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.dto.testimonial.TestimonialResponse;
import com.conlact.conlact_backend.entity.Testimonial;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestimonialService {

    private final TestimonialRepository testimonialRepository;

    public List<TestimonialResponse> getPublicTestimonials() {
        return testimonialRepository.findByIsPublishedTrueAndIsAuthorizedTrueOrderByCreatedAtDescIdAsc()
                .stream().map(TestimonialResponse::fromEntity).toList();
    }

    public List<AdminTestimonialResponse> getAllTestimonials() {
        return testimonialRepository.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
                .stream().map(AdminTestimonialResponse::fromEntity).toList();
    }

    public AdminTestimonialResponse getTestimonialById(UUID id) {
        return AdminTestimonialResponse.fromEntity(testimonialRepository.findById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public AdminTestimonialResponse createTestimonial(TestimonialRequest request) {
        boolean authorized = Boolean.TRUE.equals(request.isAuthorized());
        boolean published = Boolean.TRUE.equals(request.isPublished());
        validatePublication(authorized, published);
        Testimonial testimonial = Testimonial.builder()
                .authorName(request.authorName().trim())
                .authorType(normalizeOptionalText(request.authorType()))
                .quote(request.quote().trim())
                .isAuthorized(authorized)
                .isPublished(published)
                .build();
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public AdminTestimonialResponse updateTestimonial(UUID id, TestimonialRequest request) {
        Testimonial testimonial = findForUpdate(id);
        boolean authorized = request.isAuthorized() == null
                ? Boolean.TRUE.equals(testimonial.getIsAuthorized()) : request.isAuthorized();
        boolean published = request.isPublished() == null
                ? authorized && Boolean.TRUE.equals(testimonial.getIsPublished()) : request.isPublished();
        validatePublication(authorized, published);
        testimonial.setAuthorName(request.authorName().trim());
        testimonial.setAuthorType(normalizeOptionalText(request.authorType()));
        testimonial.setQuote(request.quote().trim());
        testimonial.setIsAuthorized(authorized);
        testimonial.setIsPublished(published);
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public AdminTestimonialResponse changePublication(UUID id, Boolean requestedState) {
        Testimonial testimonial = findForUpdate(id);
        boolean published = requestedState == null
                ? !Boolean.TRUE.equals(testimonial.getIsPublished()) : requestedState;
        validatePublication(Boolean.TRUE.equals(testimonial.getIsAuthorized()), published);
        testimonial.setIsPublished(published);
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public void deleteTestimonial(UUID id) {
        testimonialRepository.delete(findForUpdate(id));
    }

    private Testimonial findForUpdate(UUID id) {
        return testimonialRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Testimonio no encontrado con ID: " + id);
    }

    private void validatePublication(boolean authorized, boolean published) {
        if (published && !authorized) {
            throw new BadRequestException("El testimonio debe estar autorizado antes de publicarse");
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
