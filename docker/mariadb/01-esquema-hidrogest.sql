-- =====================================================================
-- Réplica LOCAL del esquema de HidroGest (Laravel 11), copiada de las migraciones de
-- https://github.com/HydroGest-Team/HydroGest/tree/main/database/migrations
-- Solo sirve para desarrollo: en producción el servicio lee la BD real de HidroGest.
-- timestamps() de Laravel = TIMESTAMP NULL (en UTC, APP_TIMEZONE=UTC).
-- =====================================================================

CREATE TABLE tb_roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre_rol ENUM('Administrador', 'Secretaria', 'Empleado') NOT NULL UNIQUE,
    descripcion_rol VARCHAR(100) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_usuarios (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    remember_token VARCHAR(100) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT tb_usuarios_role_id_foreign FOREIGN KEY (role_id) REFERENCES tb_roles (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_clientes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    dpi_cliente VARCHAR(13) NOT NULL UNIQUE,
    nombre1_cliente VARCHAR(20) NOT NULL,
    nombre2_cliente VARCHAR(20) NULL,
    nombre3_cliente VARCHAR(20) NULL,
    apellido1_cliente VARCHAR(20) NOT NULL,
    apellido2_cliente VARCHAR(20) NULL,
    apellido3_cliente VARCHAR(20) NULL,
    telefono_cliente VARCHAR(8) NULL,
    direccion_cliente VARCHAR(50) NULL,
    numero_cuenta_cliente VARCHAR(20) NULL,
    activo_cliente ENUM('ACTIVO', 'NO ACTIVO') NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_contadores (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo_contador VARCHAR(20) NOT NULL,
    sector_contador VARCHAR(50) NULL,
    activo_contador ENUM('ACTIVO', 'NO ACTIVO') NOT NULL DEFAULT 'ACTIVO',
    fecha_instalacion DATETIME NULL,
    cliente_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT tb_contadores_cliente_id_foreign FOREIGN KEY (cliente_id) REFERENCES tb_clientes (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_tipo_tarifa (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre_tipo VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(100) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_tarifas (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    monto_por_unidad DECIMAL(10, 2) NOT NULL,
    vigente_desde DATETIME NOT NULL,
    vigente_hasta DATETIME NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    cantidad_paja DECIMAL(4, 1) NULL,
    tipo_tarifa_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT tb_tarifas_tipo_tarifa_id_foreign FOREIGN KEY (tipo_tarifa_id) REFERENCES tb_tipo_tarifa (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_periodos (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    fecha_apertura DATETIME NOT NULL,
    fecha_cierre DATETIME NULL,
    estado_periodo ENUM('ACTIVO', 'CERRADO') NOT NULL DEFAULT 'ACTIVO',
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_lecturas (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    numero_recibo VARCHAR(30) NOT NULL UNIQUE,
    lectura_anterior DECIMAL(10, 2) NOT NULL DEFAULT 0,
    lectura_actual DECIMAL(10, 2) NOT NULL,
    consumo DECIMAL(10, 2) AS (lectura_actual - lectura_anterior) STORED,
    monto DECIMAL(10, 2) NOT NULL,
    fecha_lectura DATETIME NOT NULL,
    tarifa_id BIGINT UNSIGNED NOT NULL,
    usuario_id BIGINT UNSIGNED NOT NULL,
    contador_id BIGINT UNSIGNED NOT NULL,
    periodo_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT tb_lecturas_contador_id_periodo_id_unique UNIQUE (contador_id, periodo_id),
    CONSTRAINT tb_lecturas_tarifa_id_foreign FOREIGN KEY (tarifa_id) REFERENCES tb_tarifas (id) ON DELETE RESTRICT,
    CONSTRAINT tb_lecturas_usuario_id_foreign FOREIGN KEY (usuario_id) REFERENCES tb_usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT tb_lecturas_contador_id_foreign FOREIGN KEY (contador_id) REFERENCES tb_contadores (id) ON DELETE RESTRICT,
    CONSTRAINT tb_lecturas_periodo_id_foreign FOREIGN KEY (periodo_id) REFERENCES tb_periodos (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_pagos (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    monto_pago DECIMAL(10, 2) NOT NULL,
    fecha_pago DATETIME NOT NULL,
    metodo_pago ENUM('Efectivo', 'Credito', 'Debito') NOT NULL,
    estado_pago ENUM('PAGADO', 'NO PAGADO') NOT NULL DEFAULT 'NO PAGADO',
    lecturas_id BIGINT UNSIGNED NOT NULL UNIQUE,
    usuario_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT tb_pagos_lecturas_id_foreign FOREIGN KEY (lecturas_id) REFERENCES tb_lecturas (id) ON DELETE RESTRICT,
    CONSTRAINT tb_pagos_usuario_id_foreign FOREIGN KEY (usuario_id) REFERENCES tb_usuarios (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
