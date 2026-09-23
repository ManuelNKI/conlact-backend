package com.conlact.conlact_backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    @DisplayName("Debe convertir texto con tildes y mayúsculas a slug válido")
    void testToSlug_WithAccents() {
        String result = SlugUtils.toSlug("Asociación Pilahuín & Tungurahua");
        assertThat(result).isEqualTo("asociacion-pilahuin-tungurahua");
    }

    @Test
    @DisplayName("Debe manejar espacios múltiples y caracteres especiales")
    void testToSlug_SpecialChars() {
        String result = SlugUtils.toSlug("  Comunidad   El Lindero -- QUESOS!!  ");
        assertThat(result).isEqualTo("comunidad-el-lindero-quesos");
    }

    @Test
    @DisplayName("Debe retornar cadena vacía si el input es nulo o vacío")
    void testToSlug_NullOrBlank() {
        assertThat(SlugUtils.toSlug(null)).isEmpty();
        assertThat(SlugUtils.toSlug("   ")).isEmpty();
    }
}
