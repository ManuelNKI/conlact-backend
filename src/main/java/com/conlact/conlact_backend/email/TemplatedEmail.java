package com.conlact.conlact_backend.email;

import com.conlact.conlact_backend.validation.HttpUrlValidator;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.Map;
import java.util.Objects;

public record TemplatedEmail(String recipient, String subject, EmailTemplate template, Map<String, Object> variables) {
    public TemplatedEmail {
        if (recipient == null || recipient.isBlank() || recipient.contains("\r") || recipient.contains("\n")) {
            throw new IllegalArgumentException("El destinatario del correo es inválido");
        }
        recipient = recipient.trim();
        try {
            InternetAddress address = new InternetAddress(recipient, true);
            address.validate();
        } catch (AddressException ex) {
            throw new IllegalArgumentException("El destinatario del correo es inválido", ex);
        }
        if (subject == null || subject.isBlank() || subject.length() > 200 || subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("El asunto es obligatorio, sin saltos de línea y con máximo 200 caracteres");
        }
        subject = subject.trim();
        Objects.requireNonNull(template, "La plantilla del correo es obligatoria");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        for (String required : template.requiredVariables()) {
            Object value = variables.get(required);
            if (value == null || (value instanceof String text && text.isBlank())) {
                throw new IllegalArgumentException("Falta la variable de plantilla: " + required);
            }
        }
        Object actionUrl = variables.get("actionUrl");
        if (actionUrl != null && (!(actionUrl instanceof String url) || !HttpUrlValidator.isValid(url))) {
            throw new IllegalArgumentException("El enlace del correo debe ser una URL HTTP o HTTPS válida");
        }
    }
}
