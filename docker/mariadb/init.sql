-- Inicialización de la base de datos HidroGest para pagos-sender
-- Solo lectura: el usuario 'hidrogest' tiene permisos SELECT

CREATE DATABASE IF NOT EXISTS hidrogest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hidrogest;

-- Tabla de periodos
CREATE TABLE IF NOT EXISTS tb_periodos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fecha_apertura DATE NOT NULL,
    fecha_cierre DATE NOT NULL,
    estado_periodo ENUM('ACTIVO', 'CERRADO') NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de tarifas (simplificada)
CREATE TABLE IF NOT EXISTS tb_tipo_tarifa (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    descripcion VARCHAR(150)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_tarifas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tipo_tarifa_id BIGINT UNSIGNED NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha_vigencia_desde DATE NOT NULL,
    fecha_vigencia_hasta DATE,
    FOREIGN KEY (tipo_tarifa_id) REFERENCES tb_tipo_tarifa(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de usuarios
CREATE TABLE IF NOT EXISTS tb_roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_usuarios (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    FOREIGN KEY (role_id) REFERENCES tb_roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de clientes
CREATE TABLE IF NOT EXISTS tb_clientes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dpi_cliente VARCHAR(13) NOT NULL UNIQUE,
    nombre1_cliente VARCHAR(50) NOT NULL,
    nombre2_cliente VARCHAR(50),
    nombre3_cliente VARCHAR(50),
    apellido1_cliente VARCHAR(50) NOT NULL,
    apellido2_cliente VARCHAR(50),
    apellido3_cliente VARCHAR(50),
    telefono_cliente VARCHAR(20),
    direccion_cliente VARCHAR(200),
    numero_cuenta_cliente VARCHAR(30),
    activo_cliente BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de contadores
CREATE TABLE IF NOT EXISTS tb_contadores (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    codigo_contador VARCHAR(20) NOT NULL UNIQUE,
    sector_contador VARCHAR(50),
    activo_contador BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_instalacion DATE,
    cliente_id BIGINT UNSIGNED NOT NULL,
    FOREIGN KEY (cliente_id) REFERENCES tb_clientes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de lecturas
CREATE TABLE IF NOT EXISTS tb_lecturas (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    numero_recibo VARCHAR(30) NOT NULL UNIQUE,
    lectura_anterior INT UNSIGNED NOT NULL,
    lectura_actual INT UNSIGNED NOT NULL,
    consumo INT UNSIGNED NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha_lectura DATE NOT NULL,
    tarifa_id BIGINT UNSIGNED NOT NULL,
    usuario_id BIGINT UNSIGNED NOT NULL,
    contador_id BIGINT UNSIGNED NOT NULL,
    periodo_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (tarifa_id) REFERENCES tb_tarifas(id),
    FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id),
    FOREIGN KEY (contador_id) REFERENCES tb_contadores(id),
    FOREIGN KEY (periodo_id) REFERENCES tb_periodos(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de pagos (simulando la tabla del monolito Laravel)
CREATE TABLE IF NOT EXISTS tb_pagos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    monto_pago DECIMAL(12,2) NOT NULL,
    fecha_pago DATETIME NOT NULL,
    metodo_pago ENUM('Efectivo', 'Credito', 'Debito') NOT NULL,
    estado_pago ENUM('PAGADO', 'NO PAGADO') NOT NULL DEFAULT 'NO PAGADO',
    lecturas_id BIGINT UNSIGNED NOT NULL,
    usuario_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (lecturas_id) REFERENCES tb_lecturas(id),
    FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insertar datos de prueba mínimos
-- Roles
INSERT INTO tb_roles (nombre) VALUES ('ADMIN'), ('OPERADOR'), ('LECTOR');

-- Usuarios
INSERT INTO tb_usuarios (name, email, password, role_id) VALUES
('admin', 'admin@test.com', '$2a$10$xyz', 1),
('operador1', 'operador1@test.com', '$2a$10$xyz', 2);

-- Periodos
INSERT INTO tb_periodos (fecha_apertura, fecha_cierre, estado_periodo) VALUES
('2026-09-01', '2026-09-30', 'CERRADO'),
('2026-10-01', '2026-10-31', 'ACTIVO');

-- Tipos de tarifa
INSERT INTO tb_tipo_tarifa (nombre, descripcion) VALUES
('RESIDENCIAL', 'Tarifa residencial estándar'),
('COMERCIAL', 'Tarifa comercial');

-- Tarifas
INSERT INTO tb_tarifas (tipo_tarifa_id, monto, fecha_vigencia_desde, fecha_vigencia_hasta) VALUES
(1, 5.50, '2026-01-01', NULL),
(2, 8.75, '2026-01-01', NULL);

-- Clientes
INSERT INTO tb_clientes (dpi_cliente, nombre1_cliente, nombre2_cliente, nombre3_cliente, apellido1_cliente, apellido2_cliente, apellido3_cliente, telefono_cliente, direccion_cliente, numero_cuenta_cliente, activo_cliente) VALUES
('1234567890123', 'Ana', NULL, NULL, 'López', NULL, NULL, '5555-1111', 'Zona 1', 'CLI-0001', TRUE),
('2345678901234', 'Carlos', NULL, NULL, 'Méndez', NULL, NULL, '5555-2222', 'Zona 2', 'CLI-0002', TRUE),
('3456789012345', 'María', NULL, NULL, 'González', NULL, NULL, '5555-3333', 'Zona 3', 'CLI-0003', TRUE),
('4567890123456', 'Roberto', NULL, NULL, 'Pérez', NULL, NULL, '5555-4444', 'Zona 4', 'CLI-0004', TRUE),
('5678901234567', 'Laura', NULL, NULL, 'Rodríguez', NULL, NULL, '5555-5555', 'Zona 5', 'CLI-0005', TRUE),
('6789012345678', 'Jorge', NULL, NULL, 'Herrera', NULL, NULL, '5555-6666', 'Zona 6', 'CLI-0006', TRUE),
('7890123456789', 'Sofía', NULL, NULL, 'Castillo', NULL, NULL, '5555-7777', 'Zona 7', 'CLI-0007', TRUE),
('8901234567890', 'Pedro', NULL, NULL, 'Morales', NULL, NULL, '5555-8888', 'Zona 8', 'CLI-0008', TRUE);

-- Contadores
INSERT INTO tb_contadores (codigo_contador, sector_contador, activo_contador, fecha_instalacion, cliente_id) VALUES
('MTR-00001', 'Sector 1', TRUE, '2020-01-15', 1),
('MTR-00002', 'Sector 1', TRUE, '2020-02-20', 2),
('MTR-00003', 'Sector 2', TRUE, '2021-03-10', 3),
('MTR-00004', 'Sector 2', TRUE, '2021-04-05', 4),
('MTR-00005', 'Sector 3', TRUE, '2022-05-12', 5),
('MTR-00006', 'Sector 3', TRUE, '2022-06-18', 6),
('MTR-00007', 'Sector 4', TRUE, '2023-07-22', 7),
('MTR-00008', 'Sector 4', TRUE, '2023-08-30', 8);

-- Tarifas
INSERT INTO tb_tarifas (tipo_tarifa_id, monto, fecha_vigencia_desde, fecha_vigencia_hasta) VALUES
(1, 5.50, '2026-01-01', NULL),
(2, 8.75, '2026-01-01', NULL);

-- Lecturas y pagos de prueba
INSERT INTO tb_lecturas (numero_recibo, lectura_anterior, lectura_actual, consumo, monto, fecha_lectura, tarifa_id, usuario_id, contador_id, periodo_id) VALUES
('EQA-000001', 100, 120, 20, 110.00, '2026-10-01', 1, 2, 1, 2),
('EQA-000002', 200, 215, 15, 82.50, '2026-10-01', 1, 2, 2, 2),
('EQA-000003', 300, 330, 30, 165.00, '2026-10-02', 1, 2, 3, 2),
('EQA-000004', 400, 410, 10, 55.00, '2026-10-02', 1, 2, 4, 2),
('EQA-000005', 500, 540, 40, 220.00, '2026-10-03', 1, 2, 5, 2),
('EQA-000006', 600, 620, 20, 110.00, '2026-10-03', 1, 2, 6, 2),
('EQA-000007', 700, 725, 25, 137.50, '2026-10-04', 1, 2, 7, 2),
('EQA-000008', 800, 815, 15, 82.50, '2026-10-04', 1, 2, 8, 2);

INSERT INTO tb_pagos (monto_pago, fecha_pago, metodo_pago, estado_pago, lecturas_id, usuario_id, created_at, updated_at) VALUES
(110.00, '2026-10-01 10:15:00', 'Efectivo', 'PAGADO', 1, 2, '2026-10-01 10:15:00', '2026-10-01 10:15:00'),
(82.50, '2026-10-01 11:30:00', 'Credito', 'PAGADO', 2, 2, '2026-10-01 11:30:00', '2026-10-01 11:30:00'),
(165.00, '2026-10-02 09:45:00', 'Efectivo', 'PAGADO', 3, 2, '2026-10-02 09:45:00', '2026-10-02 09:45:00'),
(55.00, '2026-10-02 14:20:00', 'Debito', 'PAGADO', 4, 2, '2026-10-02 14:20:00', '2026-10-02 14:20:00'),
(220.00, '2026-10-03 10:00:00', 'Efectivo', 'PAGADO', 5, 2, '2026-10-03 10:00:00', '2026-10-03 10:00:00'),
(110.00, '2026-10-03 16:45:00', 'Credito', 'PAGADO', 6, 2, '2026-10-03 16:45:00', '2026-10-03 16:45:00'),
(137.50, '2026-10-04 11:15:00', 'Efectivo', 'PAGADO', 7, 2, '2026-10-04 11:15:00', '2026-10-04 11:15:00'),
(82.50, '2026-10-04 15:30:00', 'Debito', 'PAGADO', 8, 2, '2026-10-04 15:30:00', '2026-10-04 15:30:00');

-- Usuario de solo lectura para la aplicación
CREATE USER IF NOT EXISTS 'hidrogest'@'%' IDENTIFIED BY 'hidrogest';
GRANT SELECT ON hidrogest.* TO 'hidrogest'@'%';
FLUSH PRIVILEGES;