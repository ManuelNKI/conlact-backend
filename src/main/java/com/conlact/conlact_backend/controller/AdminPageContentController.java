package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.cms.PageContentAdminSaveResponse;
import com.conlact.conlact_backend.dto.cms.PageContentResponse;
import com.conlact.conlact_backend.dto.cms.PageContentSaveRequest;
import com.conlact.conlact_backend.service.EditorialContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/paginas")
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@RequiredArgsConstructor
public class AdminPageContentController {

    private final EditorialContentService editorialContentService;

    @GetMapping("/{slug}")
    public ResponseEntity<PageContentResponse> getPageConfig(@PathVariable String slug) {
        return ResponseEntity.ok(editorialContentService.getPublicPage(slug));
    }

    @PostMapping("/{slug}")
    public ResponseEntity<PageContentAdminSaveResponse> savePageConfig(
            @PathVariable String slug,
            @Valid @RequestBody PageContentSaveRequest request) {

        PageContentAdminSaveResponse response = editorialContentService.savePage(slug, request);
        return ResponseEntity.ok(response);
    }
}
