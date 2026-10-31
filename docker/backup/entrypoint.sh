#!/bin/sh
set -e

# ==============================================================================
# Entrypoint del servicio de respaldo cron en contenedor Alpine
# Consorcio de Lácteos de Tungurahua (CONLAC-T) - [BE-39]
# ==============================================================================

# Expresión cron: por defecto cada día a las 02:00 AM (0 2 * * *)
CRON_SCHEDULE="${BACKUP_CRON_SCHEDULE:-0 2 * * *}"

echo "=================================================================="
echo "Servicio de Respaldo Automatizado CONLAC-T PostgreSQL (BE-39)"
echo "Cron Schedule: ${CRON_SCHEDULE}"
echo "Política de Retención: ${BACKUP_RETENTION_DAYS:-7} días"
echo "=================================================================="

# Ejecutar un respaldo inicial al arrancar el contenedor
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Ejecutando respaldo inicial de comprobación al inicio..."
/usr/local/bin/backup.sh || echo "[WARN] Respaldo inicial falló o base de datos aún no lista, el cron continuará programado."

# Registrar la tarea en crontab
echo "${CRON_SCHEDULE} /usr/local/bin/backup.sh >> /var/log/cron-backup.log 2>&1" > /etc/crontabs/root

# Iniciar crond en foreground con logeo a consola
echo "[$(date +'%Y-%m-%d %H:%M:%S')] Demonio cron iniciado. Monitoreando tareas..."
exec crond -f -l 2
