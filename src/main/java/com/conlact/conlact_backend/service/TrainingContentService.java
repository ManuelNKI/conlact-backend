package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.dto.training.*;
import com.conlact.conlact_backend.entity.TrainingContent;
import com.conlact.conlact_backend.entity.TrainingSection;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import com.conlact.conlact_backend.repository.TrainingContentRepository;
import com.conlact.conlact_backend.repository.TrainingSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingContentService {

    private final TrainingSectionRepository sectionRepository;
    private final TrainingContentRepository contentRepository;

    /**
     * Vista pública: sólo secciones publicadas con contenidos publicados.
     */
    @Transactional(readOnly = true)
    public List<TrainingSectionResponse> getPublicTraining() {
        return sectionRepository.findPublishedSections().stream()
                .map(s -> TrainingSectionResponse.fromEntity(s, true))
                .toList();
    }

    /**
     * Vista administrativa: todas las secciones con todos sus contenidos.
     */
    @Transactional(readOnly = true)
    public List<TrainingSectionResponse> getAllSections() {
        return sectionRepository.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .map(s -> TrainingSectionResponse.fromEntity(s, false))
                .toList();
    }

    @Transactional
    public TrainingSectionResponse createSection(TrainingSectionCreateRequest request) {
        List<TrainingSection> all = sectionRepository.findAllByOrderBySortOrderAscCreatedAtAsc();
        int nextOrder = all.isEmpty() ? 0 : all.get(all.size() - 1).getSortOrder() + 1;

        TrainingSection section = TrainingSection.builder()
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .isPublished(Boolean.TRUE.equals(request.isPublished()))
                .sortOrder(nextOrder)
                .build();

        TrainingSection saved = sectionRepository.save(section);
        return TrainingSectionResponse.fromEntity(saved, false);
    }

    @Transactional
    public TrainingSectionResponse patchSection(UUID id, TrainingSectionPatchRequest request) {
        TrainingSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sección no encontrada con id: " + id));

        if (request.move() != null && !request.move().isBlank()) {
            moveSectionOrder(section, request.move().trim().toLowerCase());
            return TrainingSectionResponse.fromEntity(section, false);
        }

        if (request.title() != null) {
            String cleanTitle = request.title().trim();
            if (cleanTitle.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título de la sección debe tener al menos 3 caracteres.");
            }
            section.setTitle(cleanTitle);
        }

        if (request.description() != null) {
            section.setDescription(request.description().trim().isEmpty() ? null : request.description().trim());
        }

        if (request.isPublished() != null) {
            section.setIsPublished(request.isPublished());
        }

        TrainingSection updated = sectionRepository.save(section);
        return TrainingSectionResponse.fromEntity(updated, false);
    }

    @Transactional
    public void deleteSection(UUID id) {
        if (!sectionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Sección no encontrada con id: " + id);
        }
        sectionRepository.deleteById(id);
    }

    @Transactional
    public TrainingContentResponse createContent(TrainingContentCreateRequest request) {
        TrainingSection section = sectionRepository.findById(request.sectionId())
                .orElseThrow(() -> new ResourceNotFoundException("La sección indicada no existe."));

        validateContentFields(request.type(), request.url(), request.body());

        List<TrainingContent> existing = contentRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId());
        int nextOrder = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;

        TrainingContent content = TrainingContent.builder()
                .section(section)
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .contentType(request.type().trim().toLowerCase())
                .url(request.url() != null ? request.url().trim() : null)
                .body(request.body() != null ? request.body().trim() : null)
                .sortOrder(nextOrder)
                .isPublished(Boolean.TRUE.equals(request.isPublished()))
                .build();

        TrainingContent saved = contentRepository.save(content);
        return TrainingContentResponse.fromEntity(saved);
    }

    @Transactional
    public TrainingContentResponse patchContent(UUID id, TrainingContentPatchRequest request) {
        TrainingContent content = contentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contenido no encontrado con id: " + id));

        if (request.move() != null && !request.move().isBlank()) {
            moveContentOrder(content, request.move().trim().toLowerCase());
            return TrainingContentResponse.fromEntity(content);
        }

        if (request.title() != null) {
            String cleanTitle = request.title().trim();
            if (cleanTitle.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título debe tener al menos 3 caracteres.");
            }
            content.setTitle(cleanTitle);
        }

        if (request.description() != null) {
            content.setDescription(request.description().trim().isEmpty() ? null : request.description().trim());
        }

        if (request.type() != null) {
            content.setContentType(request.type().trim().toLowerCase());
        }

        if (request.url() != null) {
            content.setUrl(request.url().trim().isEmpty() ? null : request.url().trim());
        }

        if (request.body() != null) {
            content.setBody(request.body().trim().isEmpty() ? null : request.body().trim());
        }

        validateContentFields(content.getContentType(), content.getUrl(), content.getBody());

        if (request.isPublished() != null) {
            content.setIsPublished(request.isPublished());
        }

        TrainingContent updated = contentRepository.save(content);
        return TrainingContentResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteContent(UUID id) {
        if (!contentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Contenido no encontrado con id: " + id);
        }
        contentRepository.deleteById(id);
    }

    private void validateContentFields(String type, String url, String body) {
        if ("texto".equalsIgnoreCase(type)) {
            if (body == null || body.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido de tipo texto necesita un cuerpo.");
            }
        } else {
            if (url == null || url.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este tipo de contenido necesita una URL.");
            }
        }
    }

    private void moveSectionOrder(TrainingSection section, String direction) {
        List<TrainingSection> all = sectionRepository.findAllByOrderBySortOrderAscCreatedAtAsc();
        int index = all.indexOf(section);
        if (index == -1) return;

        if ("arriba".equals(direction) && index > 0) {
            TrainingSection prev = all.get(index - 1);
            int temp = section.getSortOrder();
            section.setSortOrder(prev.getSortOrder());
            prev.setSortOrder(temp);
            sectionRepository.save(prev);
            sectionRepository.save(section);
        } else if ("abajo".equals(direction) && index < all.size() - 1) {
            TrainingSection next = all.get(index + 1);
            int temp = section.getSortOrder();
            section.setSortOrder(next.getSortOrder());
            next.setSortOrder(temp);
            sectionRepository.save(next);
            sectionRepository.save(section);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede mover la sección en la dirección indicada.");
        }
    }

    private void moveContentOrder(TrainingContent content, String direction) {
        List<TrainingContent> siblings = contentRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(content.getSection().getId());
        int index = siblings.indexOf(content);
        if (index == -1) return;

        if ("arriba".equals(direction) && index > 0) {
            TrainingContent prev = siblings.get(index - 1);
            int temp = content.getSortOrder();
            content.setSortOrder(prev.getSortOrder());
            prev.setSortOrder(temp);
            contentRepository.save(prev);
            contentRepository.save(content);
        } else if ("abajo".equals(direction) && index < siblings.size() - 1) {
            TrainingContent next = siblings.get(index + 1);
            int temp = content.getSortOrder();
            content.setSortOrder(next.getSortOrder());
            next.setSortOrder(temp);
            contentRepository.save(next);
            contentRepository.save(content);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede mover el contenido en la dirección indicada.");
        }
    }
}
