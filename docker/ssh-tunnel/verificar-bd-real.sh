#!/bin/bash
# Verifica, SOLO CON SELECT, que la BD real de HidroGest tenga lo que pagos-sender necesita.
# Requiere el túnel levantado:  docker compose -f docker-compose.yml -f docker-compose.real.yml up -d hidrogest-tunnel
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; source .env; set +a
docker run --rm -i --network pagos-sender_default -e MYSQL_PWD="$HIDROGEST_DB_PASSWORD" mariadb:11.4 \
  mariadb -h hidrogest-tunnel -u "$HIDROGEST_DB_USER" "$HIDROGEST_DB_NAME" <<'SQL'
SELECT VERSION() AS version, @@time_zone AS zona_bd, NOW() AS ahora_bd;
SELECT table_name AS tabla, table_rows AS filas_aprox FROM information_schema.tables
 WHERE table_schema = DATABASE() ORDER BY 1;
SELECT table_name AS tabla, column_name AS columna, column_type AS tipo FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name IN ('tb_pagos','tb_lecturas','tb_contadores','tb_clientes','tb_periodos')
 ORDER BY table_name, ordinal_position;
SELECT estado_pago, COUNT(*) AS pagos, MIN(id) AS id_min, MAX(id) AS id_max,
       MIN(fecha_pago) AS primera, MAX(fecha_pago) AS ultima
  FROM tb_pagos GROUP BY estado_pago;
-- La misma consulta que usa el servicio (sin DPI), primeros 5 pagos PAGADO.
SELECT p.id, p.monto_pago, p.fecha_pago, p.metodo_pago, l.numero_recibo, c.id AS cliente_id,
       ct.codigo_contador, DATE_FORMAT(per.fecha_apertura, '%Y-%m') AS periodo,
       COALESCE(p.updated_at, p.created_at, p.fecha_pago) AS marca_tiempo
  FROM tb_pagos p
  JOIN tb_lecturas l    ON l.id = p.lecturas_id
  JOIN tb_contadores ct ON ct.id = l.contador_id
  JOIN tb_clientes c    ON c.id = ct.cliente_id
  JOIN tb_periodos per  ON per.id = l.periodo_id
 WHERE p.estado_pago = 'PAGADO'
 ORDER BY marca_tiempo, p.id LIMIT 5;
SQL
