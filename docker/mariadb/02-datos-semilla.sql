-- =====================================================================
-- Datos semilla LOCALES (ficticios). Fechas FIJAS a propósito: los simuladores del docente son
-- compartidos y persistentes; si los datos de un pago cambiaran entre corridas, la misma llave
-- llegaría con datos distintos y el banco respondería IDEMPOTENCY_KEY_REUSED.
-- Los pagos empiezan en 900001 (llaves EQA-900001...) para no chocar con los ids bajos que
-- pueda usar el HidroGest real u otros compañeros con la misma API key.
-- =====================================================================

INSERT INTO tb_roles (id, nombre_rol, descripcion_rol, created_at, updated_at) VALUES
    (1, 'Administrador', 'Acceso total', '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (2, 'Secretaria', 'Cobros y lecturas', '2026-08-28 12:00:00', '2026-08-28 12:00:00');

-- La contraseña es un hash bcrypt de relleno (no corresponde a ninguna clave real).
INSERT INTO tb_usuarios (id, name, email, password, role_id, created_at, updated_at) VALUES
    (1, 'Administrador', 'admin@hidrogest.local', '$2y$12$0000000000000000000000000000000000000000000000000000.', 1, '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (2, 'Cajera', 'caja@hidrogest.local', '$2y$12$0000000000000000000000000000000000000000000000000000.', 2, '2026-08-28 12:00:00', '2026-08-28 12:00:00');

INSERT INTO tb_clientes (id, dpi_cliente, nombre1_cliente, nombre2_cliente, apellido1_cliente, apellido2_cliente,
                         telefono_cliente, direccion_cliente, numero_cuenta_cliente, activo_cliente, created_at, updated_at) VALUES
    (1, '1000000000101', 'Ana', 'María', 'López', 'Pérez', '55550001', 'Barrio El Centro, Jutiapa', 'CTA-0001', 'ACTIVO', '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (2, '1000000000202', 'Carlos', NULL, 'Méndez', 'Ruiz', '55550002', 'Colonia Las Flores, Jutiapa', 'CTA-0002', 'ACTIVO', '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (3, '1000000000303', 'Lucía', 'Fernanda', 'Gómez', NULL, '55550003', 'Aldea El Progreso, Jutiapa', 'CTA-0003', 'ACTIVO', '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (4, '1000000000404', 'José', 'Luis', 'Hernández', 'Castillo', NULL, 'Zona 2, Jutiapa', 'CTA-0004', 'ACTIVO', '2026-08-28 12:00:00', '2026-08-28 12:00:00');

INSERT INTO tb_contadores (id, codigo_contador, sector_contador, activo_contador, fecha_instalacion, cliente_id, created_at, updated_at) VALUES
    (1, 'MTR-00001', 'Sector Norte', 'ACTIVO', '2024-01-15 09:00:00', 1, '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (2, 'MTR-00002', 'Sector Norte', 'ACTIVO', '2024-02-20 09:00:00', 2, '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (3, 'MTR-00003', 'Sector Sur', 'ACTIVO', '2024-03-10 09:00:00', 3, '2026-08-28 12:00:00', '2026-08-28 12:00:00'),
    (4, 'MTR-00004', 'Sector Sur', 'ACTIVO', '2024-04-05 09:00:00', 4, '2026-08-28 12:00:00', '2026-08-28 12:00:00');

INSERT INTO tb_tipo_tarifa (id, nombre_tipo, descripcion, created_at, updated_at) VALUES
    (1, 'RESIDENCIAL', 'Tarifa residencial', '2026-08-28 12:00:00', '2026-08-28 12:00:00');

INSERT INTO tb_tarifas (id, monto_por_unidad, vigente_desde, vigente_hasta, cantidad_paja, tipo_tarifa_id, created_at, updated_at) VALUES
    (1, 5.50, '2026-01-01 00:00:00', NULL, 1.0, 1, '2026-08-28 12:00:00', '2026-08-28 12:00:00');

INSERT INTO tb_periodos (id, fecha_apertura, fecha_cierre, estado_periodo, created_at, updated_at) VALUES
    (1, '2026-09-01 00:00:00', '2026-09-30 23:59:59', 'CERRADO', '2026-09-01 12:00:00', '2026-10-01 12:00:00'),
    (2, '2026-10-01 00:00:00', NULL, 'ACTIVO', '2026-10-01 12:00:00', '2026-10-01 12:00:00');

INSERT INTO tb_lecturas (id, numero_recibo, lectura_anterior, lectura_actual, monto, fecha_lectura,
                         tarifa_id, usuario_id, contador_id, periodo_id, created_at, updated_at) VALUES
    (1, 'REC-2026-09-0001', 100.00, 120.00, 110.00, '2026-09-28 08:00:00', 1, 2, 1, 1, '2026-09-28 14:00:00', '2026-09-28 14:00:00'),
    (2, 'REC-2026-09-0002', 200.00, 215.00, 82.50, '2026-09-28 08:30:00', 1, 2, 2, 1, '2026-09-28 14:30:00', '2026-09-28 14:30:00'),
    (3, 'REC-2026-09-0003', 300.00, 330.00, 165.00, '2026-09-28 09:00:00', 1, 2, 3, 1, '2026-09-28 15:00:00', '2026-09-28 15:00:00'),
    (4, 'REC-2026-09-0004', 400.00, 410.00, 55.00, '2026-09-28 09:30:00', 1, 2, 4, 1, '2026-09-28 15:30:00', '2026-09-28 15:30:00'),
    (5, 'REC-2026-10-0001', 120.00, 142.00, 121.00, '2026-10-20 08:00:00', 1, 2, 1, 2, '2026-10-20 14:00:00', '2026-10-20 14:00:00'),
    (6, 'REC-2026-10-0002', 215.00, 233.00, 99.00, '2026-10-20 08:30:00', 1, 2, 2, 2, '2026-10-20 14:30:00', '2026-10-20 14:30:00');

-- 900001-900005 ya cobrados; 900006 queda "NO PAGADO" para simular un cobro posterior
-- (docker/mariadb/simular-cobro.sh).
INSERT INTO tb_pagos (id, monto_pago, fecha_pago, metodo_pago, estado_pago, lecturas_id, usuario_id, created_at, updated_at) VALUES
    (900001, 110.00, '2026-10-01 10:15:00', 'Efectivo', 'PAGADO', 1, 2, '2026-10-01 16:15:00', '2026-10-01 16:15:00'),
    (900002, 82.50, '2026-10-01 11:30:00', 'Credito', 'PAGADO', 2, 2, '2026-10-01 17:30:00', '2026-10-01 17:30:00'),
    (900003, 165.00, '2026-10-02 09:45:00', 'Efectivo', 'PAGADO', 3, 2, '2026-10-02 15:45:00', '2026-10-02 15:45:00'),
    (900004, 55.00, '2026-10-02 14:20:00', 'Debito', 'PAGADO', 4, 2, '2026-10-02 20:20:00', '2026-10-02 20:20:00'),
    (900005, 121.00, '2026-10-21 08:05:00', 'Efectivo', 'PAGADO', 5, 2, '2026-10-21 14:05:00', '2026-10-21 14:05:00'),
    (900006, 99.00, '2026-10-22 09:40:00', 'Debito', 'NO PAGADO', 6, 2, '2026-10-22 15:40:00', '2026-10-22 15:40:00');
