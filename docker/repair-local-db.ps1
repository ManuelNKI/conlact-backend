# Completa una inicialización local interrumpida sin recrear el volumen
# ni volver a ejecutar el modelo base o las semillas.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$containerName = 'conlact-postgres-dev'
$databaseName = 'conlact_local'
$databaseUser = 'postgres'

function Invoke-CheckedDocker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker falló (código $LASTEXITCODE). La reparación se detuvo."
    }
}

function Invoke-LocalSql {
    param([string[]]$Arguments)
    Invoke-CheckedDocker -Arguments (@('exec', $containerName, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
            '-U', $databaseUser, '-d', $databaseName) + $Arguments)
}

$running = Invoke-CheckedDocker -Arguments @('inspect', '--format', '{{.State.Running}}', $containerName)
if ([string]$running -ne 'true') {
    throw 'Inicie PostgreSQL con docker compose up -d antes de ejecutar la reparación.'
}

$baseReady = Invoke-LocalSql -Arguments @('-tAc', "SELECT to_regclass('public.profiles') IS NOT NULL AND to_regclass('public.associations') IS NOT NULL AND to_regprocedure('public.set_updated_at()') IS NOT NULL;")
if (([string]$baseReady).Trim() -ne 't') {
    throw 'El modelo base no está completo. Esta reparación recupera Storage, association_images y la ampliación de testimonios sobre un modelo base existente.'
}

$repositoryPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$backupDirectory = Join-Path $repositoryPath 'target/local-db-backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupName = 'conlact-local-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.dump'
$containerBackupPath = '/tmp/' + $backupName
$backupPath = Join-Path $backupDirectory $backupName

Invoke-CheckedDocker -Arguments @('exec', $containerName, 'pg_dump', '-U', $databaseUser,
        '-d', $databaseName, '-Fc', '-f', $containerBackupPath)
Invoke-CheckedDocker -Arguments @('cp', "${containerName}:${containerBackupPath}", $backupPath)
if (!(Test-Path -LiteralPath $backupPath) -or (Get-Item -LiteralPath $backupPath).Length -eq 0) {
    throw 'No se pudo verificar el respaldo. La reparación se detuvo.'
}
Write-Host "Respaldo guardado: $backupPath"

Invoke-LocalSql -Arguments @('--single-transaction', '-f', '/docker-entrypoint-initdb.d/00_auth_compat.sql',
        '-f', '/docker-entrypoint-initdb.d/03_storage_buckets.sql')

$imagesExist = Invoke-LocalSql -Arguments @('-tAc', "SELECT to_regclass('public.association_images') IS NOT NULL;")
if (([string]$imagesExist).Trim() -eq 'f') {
    Invoke-LocalSql -Arguments @('-f', '/docker-entrypoint-initdb.d/04_association_images.sql')
} else {
    Write-Host 'association_images ya existe; se conserva sin repetir su migración.'
}

Invoke-LocalSql -Arguments @('-f', '/docker-entrypoint-initdb.d/05_extend_testimonials.sql')

$schemaReady = Invoke-LocalSql -Arguments @('-tAc', "SELECT to_regclass('public.association_images') IS NOT NULL AND to_regprocedure('auth.uid()') IS NOT NULL AND EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') AND EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='Admin All on Storage Objects') AND EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='testimonials' AND column_name='is_approved');")
if (([string]$schemaReady).Trim() -ne 't') {
    throw 'La verificación final del esquema falló. Revise la salida SQL y conserve el respaldo.'
}
Write-Host 'Base local actualizada: Storage, association_images y moderación de testimonios disponibles.'
