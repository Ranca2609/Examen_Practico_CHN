#!/usr/bin/env bash
set -euo pipefail

SQLCMD="/opt/mssql-tools/bin/sqlcmd"
[ -x "$SQLCMD" ] || SQLCMD="$(command -v sqlcmd)"

DB_HOST="${DB_HOST:-sqlserver}"
DB_NAME="${DB_NAME:-CHN_Prestamos}"
APP_USER="${APP_USER:-chn_app}"

echo "[init] Esperando a que SQL Server (${DB_HOST}) acepte conexiones..."
intento=0
until "$SQLCMD" -S "$DB_HOST" -U sa -P "$SA_PASSWORD" -l 5 -Q "SELECT 1" >/dev/null 2>&1; do
  intento=$((intento + 1))
  if [ "$intento" -ge 60 ]; then
    echo "[init] ERROR: SQL Server no respondió tras 60 intentos (~3 min)." >&2
    "$SQLCMD" -S "$DB_HOST" -U sa -P "$SA_PASSWORD" -l 5 -Q "SELECT 1" >&2 || true
    exit 1
  fi
  sleep 3
done
echo "[init] SQL Server disponible (tras ${intento} intento(s))."

echo "[init] Creando la base ${DB_NAME} y el usuario de aplicación ${APP_USER}..."
# sqlcmd 13.x para Linux no admite -v, pero resuelve las variables $(...) desde el entorno.
export DB_NAME APP_USER APP_PASSWORD
"$SQLCMD" -S "$DB_HOST" -U sa -P "$SA_PASSWORD" -b -I \
  -i /scripts/00_crear_base_datos.sql

echo "[init] Verificando el acceso del usuario de aplicación..."
"$SQLCMD" -S "$DB_HOST" -U "$APP_USER" -P "$APP_PASSWORD" -d "$DB_NAME" -b -I \
  -Q "SELECT 'conexion_ok' AS estado, DB_NAME() AS base, SUSER_SNAME() AS login;"

echo "[init] Listo. Flyway aplicará las migraciones al arrancar el backend."
