#!/bin/bash
# Simula que la cajera cobra un pago que estaba NO PAGADO: pasa a PAGADO y cambia updated_at
# (procedimiento simular_cobro_tardio de la réplica local). pagos-sender NO escribe: solo lee.
#
# Uso:  docker/mariadb/simular-cobro.sh <id_pago>
set -euo pipefail

PAGO_ID="${1:?uso: $0 <id_pago>}"
docker exec -i pagos-mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE"' \
  <<< "CALL simular_cobro_tardio(${PAGO_ID});"
