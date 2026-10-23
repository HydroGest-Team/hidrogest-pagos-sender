#!/bin/bash
# Crea el usuario con el que se conecta pagos-sender: SOLO SELECT sobre la BD de HidroGest.
# Usuario y contraseña vienen de .env (DB_USER / DB_PASSWORD) vía docker-compose.
set -euo pipefail

: "${PAGOS_RO_USER:?falta PAGOS_RO_USER}"
: "${PAGOS_RO_PASSWORD:?falta PAGOS_RO_PASSWORD}"

mariadb -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${PAGOS_RO_USER}'@'%' IDENTIFIED BY '${PAGOS_RO_PASSWORD}';
GRANT SELECT ON \`${MARIADB_DATABASE}\`.* TO '${PAGOS_RO_USER}'@'%';
FLUSH PRIVILEGES;
SQL
echo "Usuario de solo lectura '${PAGOS_RO_USER}' creado"
