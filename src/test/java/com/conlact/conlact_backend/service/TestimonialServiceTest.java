package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.testimonial.TestimonialRequest;
import com.conlact.conlact_backend.dto.testimonial.TestimonialModerationRequest;
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
        when(repository.findPublicTestimonials(false)).thenReturn(List.of(testimonial));
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
        assertThat(response.isApproved()).isFalse();
        assertThat(response.isFeatured()).isFalse();
        assertThat(response.isArchived()).isFalse();
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

    @Test
    @DisplayName("Testimonios: La consulta de destacados filtra desde el repositorio")
    void shouldFilterFeaturedTestimonials() {
        when(repository.findPublicTestimonials(true)).thenReturn(List.of());
        assertThat(service.getPublicTestimonials(true)).isEmpty();
        verify(repository).findPublicTestimonials(true);
    }

    @Test
    @DisplayName("Testimonios: Archivar conserva el registro y retira publicación y destacado")
    void shouldArchiveWithoutDeleting() {
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).isAuthorized(true)
                .isApproved(true).isPublished(true).isFeatured(true).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var archived = service.archiveTestimonial(testimonial.getId());
        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.isPublished()).isFalse();
        assertThat(archived.isFeatured()).isFalse();
        assertThat(archived.isApproved()).isTrue();
        assertThatThrownBy(() -> service.approveTestimonial(testimonial.getId())).isInstanceOf(BadRequestException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("Testimonios: Rechazar aprobación retira publicación y destacado")
    void shouldUnpublishRejectedTestimonials() {
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).isAuthorized(true)
                .isApproved(true).isPublished(true).isFeatured(true).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.moderateTestimonial(testimonial.getId(), new TestimonialModerationRequest(false, null, null));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.isPublished()).isFalse();
        assertThat(result.isFeatured()).isFalse();
    }

    @Test
    @DisplayName("Testimonios: Moderación vacía y destacado sin aprobación son inválidos")
    void shouldRejectInvalidModeration() {
        assertThatThrownBy(() -> service.moderateTestimonial(UUID.randomUUID(), new TestimonialModerationRequest(null, null, null)))
                .isInstanceOf(BadRequestException.class);
        Testimonial testimonial = Testimonial.builder().id(UUID.randomUUID()).build();
        when(repository.findByIdForUpdate(testimonial.getId())).thenReturn(Optional.of(testimonial));
        assertThatThrownBy(() -> service.moderateTestimonial(testimonial.getId(), new TestimonialModerationRequest(null, true, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Testimonios: Publicación administrativa mantiene compatibilidad y devuelve avatar y calificación")
    void shouldCreateApprovedProfile() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.createTestimonial(new TestimonialRequest("Mercedes", "Aliada", "Frase", true, true,
                " https://example.com/avatar ", 5, null, true, null));
        assertThat(result.isApproved()).isTrue();
        assertThat(result.isFeatured()).isTrue();
        assertThat(result.avatarUrl()).isEqualTo("https://example.com/avatar");
        assertThat(result.rating()).isEqualTo(5);
    }

    @Test
    @DisplayName("Testimonios: Avatar inseguro, calificación fuera de rango y publicación rechazada no se guardan")
    void shouldRejectInvalidProfile() {
        assertThatThrownBy(() -> service.createTestimonial(new TestimonialRequest("Mercedes", null, "Frase", true, false,
                "javascript:alert(1)", 5, null, null, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createTestimonial(new TestimonialRequest("Mercedes", null, "Frase", true, false,
                null, 6, null, null, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createTestimonial(new TestimonialRequest("Mercedes", null, "Frase", true, true,
                null, null, false, null, null))).isInstanceOf(BadRequestException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
