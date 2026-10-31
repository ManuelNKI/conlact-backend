# ==============================================================================
# Script de Respaldo Local de PostgreSQL para Windows PowerShell con Retención
# Consorcio de Lácteos de Tungurahua (CONLAC-T) - [BE-39]
# ==============================================================================
[CmdletBinding()]
param(
    [string]$ContainerName = "conlact-postgres-dev",
    [string]$Database = "conlact_local",
    [string]$User = "postgres",
    [string]$BackupDir = "./docker/backups",
    [int]$RetentionDays = 7
)

$ErrorActionPreference = "Stop"
$Timestamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$BackupFileName = "conlact_backup_${Database}_${Timestamp}.sql"
$CompressedFileName = "${BackupFileName}.gz"

if (!(Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
}

$BackupFilePath = Join-Path $BackupDir $BackupFileName
$CompressedFilePath = Join-Path $BackupDir $CompressedFileName

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Iniciando respaldo de PostgreSQL (Contenedor: $ContainerName) [BE-39]" -ForegroundColor Cyan
Write-Host " Base de datos: $Database"
Write-Host " Destino: $CompressedFilePath"
Write-Host " Política de retención: $RetentionDays días"
Write-Host "=================================================================="

# 1. Comprobar que el contenedor de PostgreSQL esté en ejecución
$containerStatus = docker inspect -f '{{.State.Running}}' $ContainerName 2>$null
if ($containerStatus -ne "true") {
    Write-Warning "El contenedor $ContainerName no está en ejecución. Intentando respaldo directo..."
}

# 2. Ejecutar pg_dump a través de docker exec
Write-Host "Ejecutando pg_dump..." -ForegroundColor Yellow
docker exec $ContainerName pg_dump -U $User -d $Database --clean --if-exists --no-owner --no-privileges | Out-File -FilePath $BackupFilePath -Encoding utf8

# 3. Comprimir el archivo en .gz usando .NET GZipStream nativo
Write-Host "Comprimiendo respaldo en GZip..." -ForegroundColor Yellow
$inputFile = [System.IO.File]::OpenRead($BackupFilePath)
$outputFile = [System.IO.File]::Create($CompressedFilePath)
$gzipStream = New-Object System.IO.Compression.GZipStream($outputFile, [System.IO.Compression.CompressionMode]::Compress)
$inputFile.CopyTo($gzipStream)
$gzipStream.Dispose()
$outputFile.Dispose()
$inputFile.Dispose()

# Eliminar el archivo .sql sin comprimir
Remove-Item $BackupFilePath -Force

$fileSize = (Get-Item $CompressedFilePath).Length / 1KB
Write-Host "Respaldo completado con éxito: $CompressedFileName ($([math]::Round($fileSize, 2)) KB)" -ForegroundColor Green

# 4. Purgar respaldos antiguos según la política de retención
Write-Host "Aplicando política de retención ($RetentionDays días)..." -ForegroundColor Yellow
$cutoffDate = (Get-Date).AddDays(-$RetentionDays)
$oldFiles = Get-ChildItem -Path $BackupDir -Filter "conlact_backup_*.sql.gz" | Where-Object { $_.LastWriteTime -lt $cutoffDate }

if ($oldFiles) {
    Write-Host "Eliminando $($oldFiles.Count) archivos antiguos que exceden la política de retención:" -ForegroundColor Yellow
    foreach ($old in $oldFiles) {
        Write-Host " - Eliminando: $($old.Name)"
        Remove-Item $old.FullName -Force
    }
} else {
    Write-Host "No se encontraron respaldos que superen el límite de $RetentionDays días." -ForegroundColor Gray
}

$totalBackups = (Get-ChildItem -Path $BackupDir -Filter "conlact_backup_*.sql.gz").Count
Write-Host "Total de respaldos vigentes en $BackupDir : $totalBackups" -ForegroundColor Cyan
