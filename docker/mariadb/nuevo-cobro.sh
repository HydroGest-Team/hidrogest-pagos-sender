#!/bin/bash
# Simula que la cajera registra en HidroGest una lectura nueva y su pago (como lo hace Laravel).
# Lo ejecuta el usuario de la "aplicación HidroGest", NO pagos-sender (que solo lee).
#
# Uso:  docker/mariadb/nuevo-cobro.sh <monto> [Efectivo|Credito|Debito]
#   ej. docker/mariadb/nuevo-cobro.sh 135.25 Debito
set -euo pipefail

MONTO="${1:?uso: $0 <monto> [Efectivo|Credito|Debito]}"
METODO="${2:-Efectivo}"

docker exec -i pagos-mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE"' <<SQL
-- Un contador nuevo por cobro: tb_lecturas es única por (contador, período).
INSERT INTO tb_contadores (codigo_contador, sector_contador, activo_contador, fecha_instalacion, cliente_id, created_at, updated_at)
VALUES (CONCAT('MTR-SIM-', LPAD((SELECT COUNT(*) + 1 FROM tb_contadores c), 4, '0')), 'Simulado', 'ACTIVO',
        UTC_TIMESTAMP(), 1 + (SELECT COUNT(*) FROM tb_contadores c2) % 4, UTC_TIMESTAMP(), UTC_TIMESTAMP());
SET @contador = LAST_INSERT_ID();
INSERT INTO tb_lecturas (numero_recibo, lectura_anterior, lectura_actual, monto, fecha_lectura, tarifa_id, usuario_id,
                         contador_id, periodo_id, created_at, updated_at)
VALUES (CONCAT('REC-SIM-', LPAD(@contador, 6, '0')), 0, 10, ${MONTO}, UTC_TIMESTAMP(), 1, 2, @contador, 2,
        UTC_TIMESTAMP(), UTC_TIMESTAMP());
INSERT INTO tb_pagos (monto_pago, fecha_pago, metodo_pago, estado_pago, lecturas_id, usuario_id, created_at, updated_at)
VALUES (${MONTO}, CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '-06:00'), '${METODO}', 'PAGADO', LAST_INSERT_ID(), 2,
        UTC_TIMESTAMP(), UTC_TIMESTAMP());
SELECT id, monto_pago, metodo_pago, fecha_pago, estado_pago FROM tb_pagos WHERE id = LAST_INSERT_ID();
SQL
