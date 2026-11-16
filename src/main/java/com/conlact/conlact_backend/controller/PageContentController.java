package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.cms.PageContentResponse;
import com.conlact.conlact_backend.service.EditorialContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paginas")
@RequiredArgsConstructor
public class PageContentController {

    private final EditorialContentService editorialContentService;

    @GetMapping("/{slug}")
    public ResponseEntity<PageContentResponse> getPageContent(@PathVariable String slug) {
        return ResponseEntity.ok(editorialContentService.getPublicPage(slug));
    }
}
