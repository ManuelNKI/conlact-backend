package com.conlact.conlact_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    @Test
    @DisplayName("Debe cargar variables de .env en el entorno de Spring respetando comillas y saltos de línea")
    void shouldLoadDotenvPropertiesIntoEnvironment(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        String content = """
                # Comentario
                SUPABASE_URL=https://test.supabase.co
                SUPABASE_SERVICE_ROLE_KEY='sb_sec_12345'
                SUPABASE_JWT_PUBLIC_KEY="line1\\nline2"
                EMPTY_LINE_FOLLOWS=true
                
                """;
        Files.writeString(envFile, content);

        // Cambiar temporalmente user.dir para la prueba
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

        try {
            StandardEnvironment env = new StandardEnvironment();
            DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
            processor.postProcessEnvironment(env, null);

            assertThat(env.getProperty("SUPABASE_URL")).isEqualTo("https://test.supabase.co");
            assertThat(env.getProperty("SUPABASE_SERVICE_ROLE_KEY")).isEqualTo("sb_sec_12345");
            assertThat(env.getProperty("SUPABASE_JWT_PUBLIC_KEY")).isEqualTo("line1\nline2");
            assertThat(env.getProperty("EMPTY_LINE_FOLLOWS")).isEqualTo("true");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}
