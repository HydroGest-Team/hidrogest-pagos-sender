#!/bin/bash
# Simula que la cajera registra en HidroGest una lectura nueva y su pago (como lo hace Laravel).
# Usa el procedimiento simular_nuevo_cobro de la réplica local (04-simulacion-cajera.sql),
# el mismo que se puede ejecutar desde Adminer. pagos-sender NO escribe: solo lee.
#
# Uso:  docker/mariadb/nuevo-cobro.sh <monto> [Efectivo|Credito|Debito] [pendiente]
#   ej. docker/mariadb/nuevo-cobro.sh 135.25 Debito             → PAGADO, se envía en ~10 s
#       docker/mariadb/nuevo-cobro.sh 75.00 Efectivo pendiente  → NO PAGADO, cobrar después con simular-cobro.sh
set -euo pipefail

MONTO="${1:?uso: $0 <monto> [Efectivo|Credito|Debito] [pendiente]}"
METODO="${2:-Efectivo}"
PAGADO=$([ "${3:-}" = "pendiente" ] && echo 0 || echo 1)

docker exec -i pagos-mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE"' \
  <<< "CALL simular_nuevo_cobro(${MONTO}, '${METODO}', ${PAGADO});"
