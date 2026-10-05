package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.contact.ContactMessageCreateRequest;
import com.conlact.conlact_backend.dto.contact.ContactMessageResponse;
import com.conlact.conlact_backend.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/contacto", "/api/contact"})
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    /**
     * Endpoint público para procesar el envío del formulario de contacto institucional.
     * Retorna 201 Created con el ID y mensaje de confirmación para el usuario.
     */
    @PostMapping
    public ResponseEntity<ContactMessageResponse> submitContactMessage(
            @Valid @RequestBody ContactMessageCreateRequest request) {
        ContactMessageResponse response = contactService.processContactMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
