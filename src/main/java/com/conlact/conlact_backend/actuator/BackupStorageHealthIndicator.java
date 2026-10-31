package com.conlact.conlact_backend.actuator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Indicador de salud para monitoreo de la resiliencia y almacenamiento de respaldos de PostgreSQL (BE-39).
 * Expone en /actuator/health el estado del directorio de copias de seguridad,
 * la política de retención configurada y la última copia generada.
 */
@Component
public class BackupStorageHealthIndicator implements HealthIndicator {

    private final Path backupDirectory;
    private final int retentionDays;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    public BackupStorageHealthIndicator(
            @Value("${app.backup.directory:./docker/backups}") String backupDirPath,
            @Value("${app.backup.retention-days:7}") int retentionDays) {
        this.backupDirectory = Paths.get(backupDirPath);
        this.retentionDays = retentionDays;
    }

    @Override
    public Health health() {
        try {
            File dir = backupDirectory.toFile();
            if (!dir.exists()) {
                // Si el directorio no existe aún en el host, intentamos verificar si es creable
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    return Health.up()
                            .withDetail("storageStatus", "CONFIGURED_NOT_YET_INITIALIZED")
                            .withDetail("backupDirectory", backupDirectory.toAbsolutePath().toString())
                            .withDetail("retentionPolicyDays", retentionDays)
                            .withDetail("totalBackupsFound", 0)
                            .withDetail("message", "Directorio de respaldos configurado; se inicializará con el primer ciclo de respaldo.")
                            .build();
                }
            }

            File[] backupFiles = dir.listFiles((d, name) ->
                    name.endsWith(".sql.gz") || name.endsWith(".dump") || name.endsWith(".sql")
            );

            if (backupFiles == null || backupFiles.length == 0) {
                return Health.up()
                        .withDetail("storageStatus", "READY")
                        .withDetail("backupDirectory", backupDirectory.toAbsolutePath().toString())
                        .withDetail("retentionPolicyDays", retentionDays)
                        .withDetail("totalBackupsFound", 0)
                        .withDetail("message", "Directorio listo para recibir respaldos automatizados.")
                        .build();
            }

            List<File> sortedFiles = Arrays.stream(backupFiles)
                    .sorted(Comparator.comparingLong(File::lastModified))
                    .toList();

            File oldest = sortedFiles.getFirst();
            File newest = sortedFiles.getLast();

            long totalSizeBytes = Arrays.stream(backupFiles).mapToLong(File::length).sum();

            return Health.up()
                    .withDetail("storageStatus", "ACTIVE")
                    .withDetail("backupDirectory", backupDirectory.toAbsolutePath().toString())
                    .withDetail("retentionPolicyDays", retentionDays)
                    .withDetail("totalBackupsFound", sortedFiles.size())
                    .withDetail("totalStorageBytes", totalSizeBytes)
                    .withDetail("newestBackupFile", newest.getName())
                    .withDetail("newestBackupDate", ISO_FORMATTER.format(Instant.ofEpochMilli(newest.lastModified())))
                    .withDetail("oldestBackupFile", oldest.getName())
                    .withDetail("oldestBackupDate", ISO_FORMATTER.format(Instant.ofEpochMilli(oldest.lastModified())))
                    .build();

        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("backupDirectory", backupDirectory.toAbsolutePath().toString())
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
