package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.cms.PageContentAdminSaveResponse;
import com.conlact.conlact_backend.dto.cms.PageContentResponse;
import com.conlact.conlact_backend.dto.cms.PageContentSaveRequest;
import com.conlact.conlact_backend.entity.PageContent;
import com.conlact.conlact_backend.entity.Testimonial;
import com.conlact.conlact_backend.exception.ConflictException;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.repository.PageContentRepository;
import com.conlact.conlact_backend.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialContentService {

    private final PageContentRepository pageContentRepository;
    private final AssociationRepository associationRepository;
    private final TestimonialRepository testimonialRepository;

    private static final Set<String> ALLOWED_STATIC_SLUGS = Set.of(
            "home",
            "tienda",
            "asociaciones",
            "turismo",
            "nosotros",
            "como-comprar",
            "contacto",
            "footer",
            "recetas"
    );

    /**
     * Valida si el slug pertenece a la lista permitida (allowlist) o es una asociación existente.
     */
    public void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El slug de la página no puede estar vacío.");
        }

        String cleanSlug = slug.trim().toLowerCase();
        if (ALLOWED_STATIC_SLUGS.contains(cleanSlug)) {
            return;
        }

        if (cleanSlug.startsWith("asoc-")) {
            String associationSlug = cleanSlug.substring("asoc-".length());
            if (associationRepository.existsBySlug(associationSlug)) {
                return;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La asociación especificada en el slug '" + cleanSlug + "' no existe."
            );
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Slug no permitido. Solo se permiten páginas reconocidas: " + ALLOWED_STATIC_SLUGS + " o asoc-{slug_asociacion}."
        );
    }

    /**
     * Obtiene la configuración de una página por su slug (para vista pública).
     */
    @Transactional(readOnly = true)
    public PageContentResponse getPublicPage(String slug) {
        validateSlug(slug);

        return pageContentRepository.findBySlug(slug)
                .map(PageContentResponse::fromEntity)
                .orElseGet(() -> buildDefaultPage(slug));
    }

    /**
     * Guarda o actualiza la configuración de una página editable con control de concurrencia optimista.
     */
    @Transactional
    public PageContentAdminSaveResponse savePage(String slug, PageContentSaveRequest request) {
        validateSlug(slug);

        boolean isNew = !pageContentRepository.existsBySlug(slug);
        PageContent page = pageContentRepository.findBySlug(slug)
                .orElseGet(() -> PageContent.builder()
                        .slug(slug)
                        .title(request.title())
                        .contentJson(new HashMap<>())
                        .version(1L)
                        .build());

        // Control de concurrencia optimista si se envió versión y la página ya existía en BD
        if (!isNew && request.version() != null) {
            if (!Objects.equals(request.version(), page.getVersion())) {
                throw new ConflictException("Conflicto de versión al guardar la página '" + slug + "'. La página fue modificada por otro usuario.");
            }
            page.setVersion(page.getVersion() + 1L);
        }

        page.setEyebrow(request.eyebrow() != null ? request.eyebrow().trim() : "");
        page.setTitle(request.title() != null ? request.title().trim() : page.getTitle());
        page.setSubtitle(request.subtitle() != null ? request.subtitle().trim() : "");
        if (request.content() != null) {
            page.setContentJson(request.content());
        }

        PageContent saved = pageContentRepository.save(page);

        // Si es portada y se enviaron testimonios en el nivel raíz, sincronizar testimonios compartidos
        if ("home".equals(slug) && request.testimonials() != null) {
            syncHomeTestimonials(request.testimonials());
        }

        return PageContentAdminSaveResponse.builder()
                .ok(true)
                .data(PageContentResponse.fromEntity(saved))
                .message("Página guardada exitosamente")
                .build();
    }

    /**
     * Sincroniza atómicamente los testimonios enviados desde la portada.
     */
    private void syncHomeTestimonials(List<Map<String, Object>> testimonialsList) {
        for (Map<String, Object> item : testimonialsList) {
            if (item == null) continue;

            String quote = (String) item.get("quote");
            String name = (String) item.get("name");
            String role = (String) item.get("role");
            Integer rating = item.get("rating") instanceof Number num ? num.intValue() : 5;

            if (quote == null || quote.isBlank() || name == null || name.isBlank()) {
                continue;
            }

            UUID id = null;
            if (item.get("id") instanceof String idStr && !idStr.isBlank()) {
                try {
                    id = UUID.fromString(idStr);
                } catch (IllegalArgumentException ignored) {
                }
            }

            Testimonial testimonial;
            if (id != null && testimonialRepository.existsById(id)) {
                testimonial = testimonialRepository.findById(id).orElseThrow();
            } else {
                testimonial = Testimonial.builder()
                        .isAuthorized(true)
                        .isApproved(true)
                        .isPublished(true)
                        .isFeatured(true)
                        .build();
            }

            testimonial.setAuthorName(name.trim());
            testimonial.setQuote(quote.trim());
            testimonial.setAuthorType(role != null ? role.trim() : "Cliente");
            testimonial.setRating(rating != null && rating >= 1 && rating <= 5 ? rating : 5);
            testimonial.setIsAuthorized(true);
            testimonial.setIsApproved(true);
            testimonial.setIsPublished(true);

            testimonialRepository.save(testimonial);
        }
    }

    private PageContentResponse buildDefaultPage(String slug) {
        Map<String, Object> defaultContent = new HashMap<>();
        String defaultTitle = switch (slug) {
            case "home" -> "Consorcio de Lácteos Tungurahua";
            case "tienda" -> "Catálogo de Productos";
            case "asociaciones" -> "Nuestras Asociaciones";
            case "turismo" -> "Turismo Comunitario";
            case "nosotros" -> "Nuestra Historia";
            case "como-comprar" -> "Cómo Comprar";
            case "contacto" -> "Contacto Institucional";
            case "footer" -> "CONLAC-T Pilahuín";
            case "recetas" -> "Recetas Tradicionales";
            default -> slug;
        };

        return PageContentResponse.builder()
                .slug(slug)
                .eyebrow("")
                .title(defaultTitle)
                .subtitle("")
                .content(defaultContent)
                .version(0L)
                .build();
    }
}
