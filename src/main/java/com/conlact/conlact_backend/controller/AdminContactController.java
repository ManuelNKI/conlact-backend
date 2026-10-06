package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.contact.AdminContactMessageResponse;
import com.conlact.conlact_backend.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/contacto", "/api/admin/contact", "/api/admin/mensajes-contacto", "/api/admin/contact-messages"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminContactController {

    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<List<AdminContactMessageResponse>> getContactMessages(
            @RequestParam(name = "pendientes", defaultValue = "false") Boolean onlyUnresolved) {
        return ResponseEntity.ok(contactService.getContactMessages(onlyUnresolved));
    }

    @PatchMapping({"/{id}/resolver", "/{id}/resolve"})
    public ResponseEntity<AdminContactMessageResponse> markAsResolved(@PathVariable UUID id) {
        return ResponseEntity.ok(contactService.updateResolvedStatus(id, true));
    }

    @PatchMapping({"/{id}/reabrir", "/{id}/reopen"})
    public ResponseEntity<AdminContactMessageResponse> reopenMessage(@PathVariable UUID id) {
        return ResponseEntity.ok(contactService.updateResolvedStatus(id, false));
    }
}
