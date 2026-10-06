package com.conlact.conlact_backend.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssociationImageUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://storage.example.com/asociaciones/lindero/instalaciones/planta-01.webp",
            "https://storage.example.com/asociaciones/lindero/productores/productor-01.jpg",
            "https://storage.example.com/asociaciones/lindero/productores/productor-02.jpeg",
            "https://storage.example.com/asociaciones/lindero/sellos/arcsa.png",
            "https://cdn.conlact.com/logos/logo-asociacion.svg",
            "https://cdn.conlact.com/photos/image.avif",
            "https://cdn.conlact.com/photos/image.PNG",
            "https://cdn.conlact.com/photos/image.WEBP"
    })
    @DisplayName("Debe validar URLs HTTPS con extensiones permitidas")
    void shouldAcceptValidUrls(String url) {
        assertThat(AssociationImageUrlValidator.isValid(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/foto.jpg",
            "ftp://example.com/foto.jpg",
            "javascript:alert(1)",
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA",
            "file:///C:/foto.jpg",
            "https://example.com/documento.pdf",
            "https://example.com/programa.exe",
            "https://example.com/foto.gif",
            "https://example.com/",
            "not-a-valid-url"
    })
    @DisplayName("Debe rechazar protocolos no HTTPS o extensiones no permitidas")
    void shouldRejectInvalidUrls(String url) {
        assertThat(AssociationImageUrlValidator.isValid(url)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar URLs nulas o en blanco")
    void shouldRejectNullOrBlank() {
        assertThat(AssociationImageUrlValidator.isValid(null)).isFalse();
        assertThat(AssociationImageUrlValidator.isValid("")).isFalse();
        assertThat(AssociationImageUrlValidator.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar URLs mayores a 2048 caracteres")
    void shouldRejectUrlExceedingMaxChars() {
        String longPath = "a".repeat(2040);
        String longUrl = "https://example.com/" + longPath + ".jpg";
        assertThat(AssociationImageUrlValidator.isValid(longUrl)).isFalse();
    }
}
