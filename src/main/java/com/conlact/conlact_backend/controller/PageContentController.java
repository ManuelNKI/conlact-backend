package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.entity.PageContent;
import com.conlact.conlact_backend.repository.PageContentRepository;
import com.conlact.conlact_backend.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class PageContentController {
    private final PageContentRepository repository;
    private final com.conlact.conlact_backend.service.ContentMediaService media;
    private final com.conlact.conlact_backend.service.EditorialContentService editorial;
    public record PageRequest(@Size(max=200) String eyebrow,
            @NotNull @Size(max=300) String titulo, @Size(max=3000) String subtitulo,
            @NotNull Map<String, Object> contenido) {}

    @GetMapping({"/api/paginas/{slug}", "/api/admin/paginas/{slug}"})
    public PageContent get(@PathVariable String slug) {
        if(slug.equals("capacitacion")) throw new ResourceNotFoundException("Usa el catálogo público de capacitación");
        return repository.findById(slug).orElseThrow(() -> new ResourceNotFoundException("Página no encontrada"));
    }

    @RequestMapping(path="/api/admin/paginas/{slug}", method={RequestMethod.POST, RequestMethod.PUT})
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PageContent save(@PathVariable String slug, @Valid @RequestBody PageRequest request) {
        PageContent page = repository.findById(slug).orElseGet(PageContent::new);
        page.setSlug(slug);
        page.setEyebrow(request.eyebrow());
        page.setTitulo(request.titulo());
        page.setSubtitulo(request.subtitulo());
        @SuppressWarnings("unchecked")
        Map<String,Object> content = (Map<String,Object>)media.persist(request.contenido(), "association-images");
        page.setContenido(editorial.sync(slug, content, page.getContenido()));
        return repository.save(page);
    }
}
