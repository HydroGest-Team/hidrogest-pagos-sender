#!/bin/bash
# Simula lo que hace HidroGest (Laravel) al cobrar: marca un pago como PAGADO y actualiza
# updated_at. Lo ejecuta el usuario de la "aplicación HidroGest", NO pagos-sender (que solo lee).
#
# Uso:  docker/mariadb/simular-cobro.sh [id_pago]      (por defecto 900006)
set -euo pipefail

PAGO_ID="${1:-900006}"
docker exec -i pagos-mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE"' <<SQL
UPDATE tb_pagos
   SET estado_pago = 'PAGADO', updated_at = UTC_TIMESTAMP()
 WHERE id = ${PAGO_ID};
SELECT id, monto_pago, metodo_pago, estado_pago, updated_at FROM tb_pagos WHERE id = ${PAGO_ID};
SQL
