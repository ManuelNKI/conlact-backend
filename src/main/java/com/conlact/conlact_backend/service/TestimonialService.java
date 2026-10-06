package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.testimonial.AdminTestimonialResponse;
import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.dto.testimonial.TestimonialResponse;
import com.conlact.conlact_backend.dto.testimonial.TestimonialModerationRequest;
import com.conlact.conlact_backend.entity.Testimonial;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TestimonialRepository;
import com.conlact.conlact_backend.validation.HttpUrlValidator;
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
        return getPublicTestimonials(false);
    }

    public List<TestimonialResponse> getPublicTestimonials(boolean featuredOnly) {
        return testimonialRepository.findPublicTestimonials(featuredOnly)
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
        Testimonial testimonial = Testimonial.builder()
                .authorName(request.authorName().trim())
                .authorType(normalizeOptionalText(request.authorType()))
                .quote(request.quote().trim())
                .build();
        applyProfile(testimonial, request);
        applyStates(testimonial, request);
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public AdminTestimonialResponse updateTestimonial(UUID id, TestimonialRequest request) {
        Testimonial testimonial = findForUpdate(id);
        testimonial.setAuthorName(request.authorName().trim());
        testimonial.setAuthorType(normalizeOptionalText(request.authorType()));
        testimonial.setQuote(request.quote().trim());
        applyProfile(testimonial, request);
        applyStates(testimonial, request);
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public AdminTestimonialResponse changePublication(UUID id, Boolean requestedState) {
        Testimonial testimonial = findForUpdate(id);
        boolean published = requestedState == null
                ? !Boolean.TRUE.equals(testimonial.getIsPublished()) : requestedState;
        // Publicar desde la ruta administrativa existente también expresa aprobación.
        validatePublication(Boolean.TRUE.equals(testimonial.getIsAuthorized()), true,
                Boolean.TRUE.equals(testimonial.getIsArchived()), published);
        if (published) {
            testimonial.setIsApproved(true);
        }
        testimonial.setIsPublished(published);
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    @Transactional
    public void deleteTestimonial(UUID id) {
        testimonialRepository.delete(findForUpdate(id));
    }

    @Transactional
    public AdminTestimonialResponse approveTestimonial(UUID id) {
        return changePublication(id, true);
    }

    @Transactional
    public AdminTestimonialResponse archiveTestimonial(UUID id) {
        return moderateTestimonial(id, new TestimonialModerationRequest(null, null, true));
    }

    @Transactional
    public AdminTestimonialResponse moderateTestimonial(UUID id, TestimonialModerationRequest request) {
        if (request.isApproved() == null && request.isFeatured() == null && request.isArchived() == null) {
            throw new BadRequestException("Envíe al menos un estado de moderación");
        }
        Testimonial testimonial = findForUpdate(id);
        applyStates(testimonial, new TestimonialRequest(testimonial.getAuthorName(), testimonial.getAuthorType(),
                testimonial.getQuote(), null, null, null, null,
                request.isApproved(), request.isFeatured(), request.isArchived()));
        return AdminTestimonialResponse.fromEntity(testimonialRepository.saveAndFlush(testimonial));
    }

    private void applyProfile(Testimonial testimonial, TestimonialRequest request) {
        if (request.avatarUrl() != null) {
            String avatarUrl = normalizeOptionalText(request.avatarUrl());
            if (avatarUrl != null && !HttpUrlValidator.isValid(avatarUrl)) {
                throw new BadRequestException("El avatar debe tener una URL HTTP o HTTPS válida");
            }
            testimonial.setAvatarUrl(avatarUrl);
        }
        if (request.rating() != null) {
            if (request.rating() < 1 || request.rating() > 5) {
                throw new BadRequestException("La calificación debe estar entre 1 y 5");
            }
            testimonial.setRating(request.rating());
        }
    }

    private void applyStates(Testimonial testimonial, TestimonialRequest request) {
        boolean authorized = request.isAuthorized() == null
                ? Boolean.TRUE.equals(testimonial.getIsAuthorized()) : request.isAuthorized();
        boolean approved = request.isApproved() == null
                ? Boolean.TRUE.equals(testimonial.getIsApproved()) : request.isApproved();
        boolean archived = request.isArchived() == null
                ? Boolean.TRUE.equals(testimonial.getIsArchived()) : request.isArchived();
        boolean featured = request.isFeatured() == null
                ? Boolean.TRUE.equals(testimonial.getIsFeatured()) : request.isFeatured();
        boolean published = request.isPublished() == null
                ? Boolean.TRUE.equals(testimonial.getIsPublished()) : request.isPublished();
        if (Boolean.TRUE.equals(request.isPublished()) && request.isApproved() == null) {
            approved = true;
        }
        if (!authorized || !approved || archived) {
            if (Boolean.TRUE.equals(request.isPublished())) {
                validatePublication(authorized, approved, archived, true);
            }
            published = false;
        }
        if (!approved || archived) {
            if (Boolean.TRUE.equals(request.isFeatured())) {
                throw new BadRequestException("Solo se pueden destacar testimonios aprobados y no archivados");
            }
            featured = false;
        }
        testimonial.setIsAuthorized(authorized);
        testimonial.setIsApproved(approved);
        testimonial.setIsArchived(archived);
        testimonial.setIsFeatured(featured);
        testimonial.setIsPublished(published);
    }

    private Testimonial findForUpdate(UUID id) {
        return testimonialRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Testimonio no encontrado con ID: " + id);
    }

    private void validatePublication(boolean authorized, boolean approved, boolean archived, boolean published) {
        if (published && !authorized) {
            throw new BadRequestException("El testimonio debe estar autorizado antes de publicarse");
        }
        if (published && (!approved || archived)) {
            throw new BadRequestException("El testimonio debe estar aprobado y no archivado antes de publicarse");
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
