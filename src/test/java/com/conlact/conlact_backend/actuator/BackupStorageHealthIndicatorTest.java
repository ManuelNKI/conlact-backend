package com.conlact.conlact_backend.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BE-39: Pruebas Unitarias para BackupStorageHealthIndicator")
class BackupStorageHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Debe reportar UP y estado READY cuando el directorio está vacío y listo")
    void shouldReportReadyWhenDirectoryIsEmpty() {
        BackupStorageHealthIndicator indicator = new BackupStorageHealthIndicator(
                tempDir.toAbsolutePath().toString(),
                7
        );

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("storageStatus", "READY");
        assertThat(health.getDetails()).containsEntry("retentionPolicyDays", 7);
        assertThat(health.getDetails()).containsEntry("totalBackupsFound", 0);
    }

    @Test
    @DisplayName("Debe reportar UP y estado ACTIVE identificando archivos más recientes y antiguos")
    void shouldReportActiveWhenBackupsExist() throws IOException, InterruptedException {
        // Crear dos archivos de respaldo simulados con marcas de tiempo distintas
        File olderBackup = Files.createFile(tempDir.resolve("conlact_backup_conlact_local_20261001_020000.sql.gz")).toFile();
        Files.writeString(olderBackup.toPath(), "mock backup 1");
        olderBackup.setLastModified(System.currentTimeMillis() - 5000);

        File newerBackup = Files.createFile(tempDir.resolve("conlact_backup_conlact_local_20261002_020000.sql.gz")).toFile();
        Files.writeString(newerBackup.toPath(), "mock backup 2 content");
        newerBackup.setLastModified(System.currentTimeMillis());

        BackupStorageHealthIndicator indicator = new BackupStorageHealthIndicator(
                tempDir.toAbsolutePath().toString(),
                14
        );

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("storageStatus", "ACTIVE");
        assertThat(health.getDetails()).containsEntry("retentionPolicyDays", 14);
        assertThat(health.getDetails()).containsEntry("totalBackupsFound", 2);
        assertThat(health.getDetails()).containsEntry("newestBackupFile", newerBackup.getName());
        assertThat(health.getDetails()).containsEntry("oldestBackupFile", olderBackup.getName());
        assertThat(health.getDetails()).containsKey("totalStorageBytes");
    }

    @Test
    @DisplayName("Debe inicializar el directorio si no existe y reportar UP")
    void shouldCreateDirectoryIfNotExists() {
        Path subDir = tempDir.resolve("auto_created_backups");
        assertThat(subDir).doesNotExist();

        BackupStorageHealthIndicator indicator = new BackupStorageHealthIndicator(
                subDir.toAbsolutePath().toString(),
                7
        );

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(subDir).exists();
    }
}
