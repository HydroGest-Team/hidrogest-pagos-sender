-- =====================================================================
-- SOLO RÉPLICA LOCAL. Procedimientos que imitan lo que hace la cajera en HidroGest (Laravel):
-- registrar una lectura con su pago y cobrar un pago pendiente. Se ejecutan desde Adminer
-- (http://localhost:8081 → hidrogest → Rutinas → Call) o desde docker/mariadb/*.sh.
-- pagos-sender NO los usa: su usuario solo tiene SELECT.
-- =====================================================================

-- Ids únicos por instalación. La llave de idempotencia es EQA-<id>, y los simuladores del docente
-- son compartidos y recuerdan cada llave. Si los cobros simulados siguieran a la semilla (900007,
-- 900008...), al recrear la réplica (docker compose down -v) o en la réplica de otro compañero
-- se repetirían llaves ya usadas con otros datos (ADR-0007). Por eso los cobros simulados empiezan
-- en <segundos unix de esta inicialización> × 1000: cada instalación tiene su propio rango
-- (p. ej. 1761600000000, llave EQA-1761600000000). La semilla 900001-900006 no cambia.
-- Solo se ejecuta al crear el volumen; la BD real de HidroGest no se toca.
SET @base_ids_simulados = UNIX_TIMESTAMP() * 1000;
SET @ddl = CONCAT('ALTER TABLE tb_pagos AUTO_INCREMENT = ', @base_ids_simulados);
PREPARE ajustar_ids FROM @ddl;
EXECUTE ajustar_ids;
DEALLOCATE PREPARE ajustar_ids;

DELIMITER //

-- Registra un cobro nuevo. p_pagado = 1 lo deja PAGADO (se envía en ~10 s);
-- p_pagado = 0 lo deja NO PAGADO (no se envía hasta cobrarlo con simular_cobro_tardio).
CREATE OR REPLACE PROCEDURE simular_nuevo_cobro(
    IN p_monto DECIMAL(10, 2),
    IN p_metodo VARCHAR(10),
    IN p_pagado BOOLEAN
)
BEGIN
    DECLARE v_n BIGINT;
    DECLARE v_cliente BIGINT;
    DECLARE v_contador BIGINT;
    DECLARE v_lectura BIGINT;
    DECLARE v_offset BIGINT;

    -- Reparte los cobros simulados entre los clientes existentes.
    SELECT COUNT(*) + 1 INTO v_n FROM tb_contadores;
    SELECT MOD(v_n, COUNT(*)) INTO v_offset FROM tb_clientes;
    SELECT id INTO v_cliente FROM tb_clientes ORDER BY id LIMIT 1 OFFSET v_offset;

    -- Un contador nuevo por cobro: tb_lecturas es única por (contador, período).
    INSERT INTO tb_contadores (codigo_contador, sector_contador, activo_contador, fecha_instalacion,
                               cliente_id, created_at, updated_at)
    VALUES (CONCAT('MTR-SIM-', LPAD(v_n, 5, '0')), 'Simulado', 'ACTIVO', UTC_TIMESTAMP(),
            v_cliente, UTC_TIMESTAMP(), UTC_TIMESTAMP());
    SET v_contador = LAST_INSERT_ID();

    INSERT INTO tb_lecturas (numero_recibo, lectura_anterior, lectura_actual, monto, fecha_lectura,
                             tarifa_id, usuario_id, contador_id, periodo_id, created_at, updated_at)
    VALUES (CONCAT('REC-SIM-', LPAD(v_contador, 6, '0')), 0, 10, p_monto, UTC_TIMESTAMP(),
            (SELECT MIN(id) FROM tb_tarifas), 2, v_contador, (SELECT MAX(id) FROM tb_periodos),
            UTC_TIMESTAMP(), UTC_TIMESTAMP());
    SET v_lectura = LAST_INSERT_ID();

    -- fecha_pago en hora de Guatemala (UTC-6), como la captura la cajera; timestamps en UTC (Laravel).
    INSERT INTO tb_pagos (monto_pago, fecha_pago, metodo_pago, estado_pago, lecturas_id, usuario_id,
                          created_at, updated_at)
    VALUES (p_monto, CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '-06:00'), p_metodo,
            IF(p_pagado, 'PAGADO', 'NO PAGADO'), v_lectura, 2, UTC_TIMESTAMP(), UTC_TIMESTAMP());

    -- Igual que IdempotencyKeys (%06d): rellena hasta 6 dígitos sin recortar los ids más largos.
    SELECT id AS pago_id, CONCAT('EQA-', LPAD(id, GREATEST(6, LENGTH(id)), '0')) AS llave_por_defecto,
           monto_pago, metodo_pago,
           estado_pago, fecha_pago
      FROM tb_pagos WHERE id = LAST_INSERT_ID();
END //

-- Cobra un pago que estaba NO PAGADO (pasa a PAGADO y cambia updated_at, como Laravel).
CREATE OR REPLACE PROCEDURE simular_cobro_tardio(IN p_pago_id BIGINT UNSIGNED)
BEGIN
    DECLARE v_cambios INT;
    UPDATE tb_pagos
       SET estado_pago = 'PAGADO', updated_at = UTC_TIMESTAMP()
     WHERE id = p_pago_id AND estado_pago = 'NO PAGADO';
    SET v_cambios = ROW_COUNT();
    SELECT id AS pago_id, monto_pago, metodo_pago, estado_pago, updated_at,
           IF(v_cambios = 1, 'cobrado ahora', 'no estaba NO PAGADO: sin cambios') AS resultado
      FROM tb_pagos WHERE id = p_pago_id;
END //

DELIMITER ;
