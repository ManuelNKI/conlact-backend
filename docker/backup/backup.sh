#!/bin/sh
set -e

# ==============================================================================
# Script de Respaldo Automatizado de PostgreSQL con Política de Retención
# Consorcio de Lácteos de Tungurahua (CONLAC-T) - [BE-39]
# ==============================================================================

# Variables de entorno o valores por defecto
PGHOST="${POSTGRES_HOST:-postgres}"
PGPORT="${POSTGRES_PORT:-5432}"
PGUSER="${POSTGRES_USER:-postgres}"
PGDATABASE="${POSTGRES_DB:-conlact_local}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="conlact_backup_${PGDATABASE}_${TIMESTAMP}.sql.gz"
BACKUP_FILEPATH="${BACKUP_DIR}/${BACKUP_FILENAME}"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] Iniciando respaldo de PostgreSQL..."
echo "  Base de datos: ${PGDATABASE}"
echo "  Host: ${PGHOST}:${PGPORT}"
echo "  Destino: ${BACKUP_FILEPATH}"
echo "  Política de retención: ${RETENTION_DAYS} días"

# Crear directorio si no existe
mkdir -p "${BACKUP_DIR}"

# Esperar a que PostgreSQL esté listo
until pg_isready -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}"; do
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] Esperando disponibilidad de PostgreSQL..."
  sleep 2
done

# Ejecutar pg_dump con compresión gzip
if pg_dump -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" --clean --if-exists --no-owner --no-privileges | gzip > "${BACKUP_FILEPATH}"; then
  BACKUP_SIZE=$(ls -lh "${BACKUP_FILEPATH}" | awk '{print $5}')
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] Respaldo completado exitosamente: ${BACKUP_FILENAME} (${BACKUP_SIZE})"
else
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: Falló el respaldo de la base de datos."
  exit 1
fi

# ==============================================================================
# Aplicación de Política de Retención: Eliminar respaldos más antiguos que RETENTION_DAYS
# ==============================================================================
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Aplicando política de retención (${RETENTION_DAYS} días)..."
OLD_BACKUPS=$(find "${BACKUP_DIR}" -name "conlact_backup_*.sql.gz" -type f -mtime +"${RETENTION_DAYS}")

if [ -n "${OLD_BACKUPS}" ]; then
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] Se encontraron respaldos obsoletos a purgar:"
  echo "${OLD_BACKUPS}"
  find "${BACKUP_DIR}" -name "conlact_backup_*.sql.gz" -type f -mtime +"${RETENTION_DAYS}" -exec rm -f {} +
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] Depuración de respaldos antiguos finalizada."
else
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] No se encontraron respaldos que excedan los ${RETENTION_DAYS} días de antigüedad."
fi

TOTAL_BACKUPS=$(find "${BACKUP_DIR}" -name "conlact_backup_*.sql.gz" -type f | wc -l)
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Resumen: ${TOTAL_BACKUPS} respaldos disponibles en ${BACKUP_DIR}."
