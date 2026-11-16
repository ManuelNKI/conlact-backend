package com.conlact.conlact_backend.dto.cms;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record PageContentSaveRequest(
        @Size(max = 60, message = "El eyebrow no puede superar los 60 caracteres")
        String eyebrow,

        @NotBlank(message = "El título de la página es obligatorio")
        @Size(max = 150, message = "El título no puede superar los 150 caracteres")
        @JsonProperty("titulo")
        String title,

        @Size(max = 300, message = "El subtítulo no puede superar los 300 caracteres")
        @JsonProperty("subtitulo")
        String subtitle,

        @JsonProperty("contenido")
        Map<String, Object> content,

        Long version,

        @JsonProperty("testimonios")
        List<Map<String, Object>> testimonials
) {
}
