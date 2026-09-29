package com.conlact.conlact_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MigrationIntegrityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("conlact_migration_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Test
    @DisplayName("Debe ejecutar la migración SQL real sobre PostgreSQL limpio sin errores")
    void shouldExecuteRealMigrationScriptOnCleanPostgres() throws Exception {
        // 1. Conectar a PostgreSQL en el contenedor efímero
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        ); Statement stmt = conn.createStatement()) {

            // 2. Ejecutar DDL base (docker/init/01_CONLACT_Modelo_Base.sql)
            String baseSql = Files.readString(Path.of("docker/init/01_CONLACT_Modelo_Base.sql"));
            stmt.execute(baseSql);

            // 3. Ejecutar la migración real de Supabase (supabase/migrations/20260928_create_association_images.sql)
            String migrationSql = Files.readString(Path.of("supabase/migrations/20260928_create_association_images.sql"));
            stmt.execute(migrationSql);

            // 4. Verificar que la tabla association_images exista en public
            ResultSet rsTable = stmt.executeQuery("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'association_images'
            """);
            assertThat(rsTable.next()).isTrue();
            assertThat(rsTable.getString("table_name")).isEqualTo("association_images");

            // 5. Verificar que el tipo enumerado association_image_type exista
            ResultSet rsEnum = stmt.executeQuery("""
                SELECT typname FROM pg_type WHERE typname = 'association_image_type'
            """);
            assertThat(rsEnum.next()).isTrue();

            // 6. Verificar que el trigger esté instalado
            ResultSet rsTrigger = stmt.executeQuery("""
                SELECT trigger_name FROM information_schema.triggers
                WHERE event_object_table = 'association_images'
                  AND trigger_name = 'trg_association_images_updated_at'
            """);
            assertThat(rsTrigger.next()).isTrue();

            // 7. Verificar que RLS esté activado
            ResultSet rsRls = stmt.executeQuery("""
                SELECT rowsecurity FROM pg_tables
                WHERE schemaname = 'public' AND tablename = 'association_images'
            """);
            assertThat(rsRls.next()).isTrue();
            assertThat(rsRls.getBoolean("rowsecurity")).isTrue();
        }
    }
}
