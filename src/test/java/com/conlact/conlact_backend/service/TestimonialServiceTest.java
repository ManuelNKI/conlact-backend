package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.entity.Testimonial;
import com.conlact.conlact_backend.exception.BadRequestException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TestimonialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestimonialServiceTest {
    @Mock private TestimonialRepository repository;
    @InjectMocks private TestimonialService service;

    @Test
    @DisplayName("BE-15: La consulta pública utiliza exclusivamente testimonios autorizados y publicados")
    void shouldQueryOnlyApprovedTestimonials() {
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).authorName("Mercedes")
                .quote("Queso de nuestra comunidad").isAuthorized(true).isPublished(true).build();
        when(repository.findByIsPublishedTrueAndIsAuthorizedTrueOrderByCreatedAtDescIdAsc()).thenReturn(List.of(testimonial));
        assertThat(service.getPublicTestimonials()).singleElement().satisfies(response -> {
            assertThat(response.authorName()).isEqualTo("Mercedes");
            assertThat(response.quote()).isEqualTo("Queso de nuestra comunidad");
        });
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("BE-15: Un testimonio nuevo queda sin autorización ni publicación por defecto")
    void shouldDefaultToUnpublishedAndNormalizeText() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createTestimonial(new TestimonialRequest(" Mercedes ", " ", " Nuestra tradición ", null, null));
        assertThat(response.authorName()).isEqualTo("Mercedes");
        assertThat(response.authorType()).isNull();
        assertThat(response.quote()).isEqualTo("Nuestra tradición");
        assertThat(response.isAuthorized()).isFalse();
        assertThat(response.isPublished()).isFalse();
    }

    @Test
    @DisplayName("BE-15: No permite crear ni publicar un testimonio sin autorización")
    void shouldRejectPublicationWithoutAuthorization() {
        assertThatThrownBy(() -> service.createTestimonial(new TestimonialRequest("Mercedes", null, "Frase", false, true)))
                .isInstanceOf(BadRequestException.class);
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).isAuthorized(false).isPublished(false).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        assertThatThrownBy(() -> service.changePublication(testimonial.getId(), null)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("BE-15: Revocar autorización retira automáticamente la publicación")
    void shouldUnpublishWhenAuthorizationIsRevoked() {
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).isAuthorized(true).isPublished(true).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.updateTestimonial(testimonial.getId(), new TestimonialRequest("Mercedes", null, "Frase", false, null));
        assertThat(result.isAuthorized()).isFalse();
        assertThat(result.isPublished()).isFalse();
    }

    @Test
    @DisplayName("BE-15: La publicación explícita mantiene su estado en reintentos")
    void shouldSupportIdempotentPublication() {
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).isAuthorized(true).isPublished(true).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.changePublication(testimonial.getId(), true).isPublished()).isTrue();
        assertThat(service.changePublication(testimonial.getId(), true).isPublished()).isTrue();
    }

    @Test
    @DisplayName("BE-15: Una escritura sobre un testimonio inexistente devuelve 404")
    void shouldRejectMissingTestimonial() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTestimonial(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
