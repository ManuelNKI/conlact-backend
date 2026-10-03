package com.conlact.conlact_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Carga automáticamente variables de un archivo .env en la raíz del proyecto
 * en el entorno de Spring Boot antes de resolver application.properties.
 */
@Slf4j
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envPath = resolveEnvPath();

        if (envPath == null || !Files.exists(envPath)) {
            return;
        }

        Map<String, Object> envProperties = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Ignorar líneas vacías y comentarios
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    String value = trimmed.substring(eqIdx + 1).trim();

                    // Remover comillas envolventes simples o dobles
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }

                    // Resolver saltos de línea literales escapados (útiles para claves PEM de JWT)
                    value = value.replace("\\n", "\n");

                    envProperties.put(key, value);
                }
            }

            if (!envProperties.isEmpty()) {
                // Registrar las propiedades con alta prioridad en el entorno de Spring
                environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", envProperties));
                log.info("Archivo .env cargado exitosamente desde {} ({} variables configuradas)", envPath.toAbsolutePath(), envProperties.size());
            }
        } catch (IOException e) {
            log.warn("No se pudo leer el archivo .env en {}: {}", envPath, e.getMessage());
        }
    }

    private Path resolveEnvPath() {
        // 1. Probar en user.dir (directorio de trabajo del proceso o entorno de test)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            Path userDirPath = Paths.get(userDir, ".env");
            if (Files.exists(userDirPath)) {
                return userDirPath;
            }
            Path subPath = Paths.get(userDir, "conlact-backend", ".env");
            if (Files.exists(subPath)) {
                return subPath;
            }
            Path parent = Paths.get(userDir).getParent();
            if (parent != null) {
                Path parentEnv = parent.resolve(".env");
                if (Files.exists(parentEnv)) {
                    return parentEnv;
                }
            }
        }

        // 2. Probar en directorio de ejecución relativo
        Path localPath = Paths.get(".env");
        if (Files.exists(localPath)) {
            return localPath;
        }

        return null;
    }

    @Override
    public int getOrder() {
        // Ejecutar antes de ConfigDataEnvironmentPostProcessor para que application.properties pueda consumir las variables
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
