package gt.edu.umg.desarrolloweb.pagos.infrastructure.mariadb;

import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MariaDbPaymentSource implements PaymentSource {

    private static final String FIND_ELEGIBLES_SINCE = """
        SELECT p.id, p.monto_pago, p.fecha_pago, p.metodo_pago, p.estado_pago,
               p.lecturas_id, l.numero_recibo, l.usuario_id, l.periodo_id,
               c.id as cliente_id, c.dpi_cliente,
               CONCAT_WS(' ', c.nombre1_cliente, c.nombre2_cliente, c.nombre3_cliente,
                         c.apellido1_cliente, c.apellido2_cliente, c.apellido3_cliente) as nombre_completo,
               ct.codigo_contador,
               per.fecha_apertura, per.fecha_cierre,
               p.created_at, p.updated_at
        FROM tb_pagos p
        JOIN tb_lecturas l ON p.lecturas_id = l.id
        JOIN tb_clientes c ON l.usuario_id = c.id
        JOIN tb_contadores ct ON l.contador_id = ct.id
        JOIN tb_periodos per ON l.periodo_id = per.id
        WHERE p.estado_pago = 'PAGADO'
          AND p.updated_at >= ?
        ORDER BY p.updated_at, p.id
        LIMIT ?
        """;

    private static final String FIND_BY_ID = """
        SELECT p.id, p.monto_pago, p.fecha_pago, p.metodo_pago, p.estado_pago,
               p.lecturas_id, l.numero_recibo, l.usuario_id, l.periodo_id,
               c.id as cliente_id, c.dpi_cliente,
               CONCAT_WS(' ', c.nombre1_cliente, c.nombre2_cliente, c.nombre3_cliente,
                         c.apellido1_cliente, c.apellido2_cliente, c.apellido3_cliente) as nombre_completo,
               ct.codigo_contador,
               per.fecha_apertura, per.fecha_cierre,
               p.created_at, p.updated_at
        FROM tb_pagos p
        JOIN tb_lecturas l ON p.lecturas_id = l.id
        JOIN tb_clientes c ON l.usuario_id = c.id
        JOIN tb_contadores ct ON l.contador_id = ct.id
        JOIN tb_periodos per ON l.periodo_id = per.id
        WHERE p.id = ?
        """;

    private static final String FIND_MAX_UPDATED_AT = """
        SELECT MAX(updated_at) FROM tb_pagos
        """;

    private final JdbcTemplate jdbcTemplate;

    public MariaDbPaymentSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Payment> findElegiblesSince(java.time.Instant since, int limit) {
        LocalDateTime sinceDateTime = LocalDateTime.ofInstant(since, Payment.ZONA_HORARIA);
        return jdbcTemplate.query(FIND_ELEGIBLES_SINCE, paymentRowMapper(), sinceDateTime, limit);
    }

    @Override
    public Payment findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, paymentRowMapper(), id);
    }

    @Override
    public java.time.Instant findMaxUpdatedAt() {
        LocalDateTime max = jdbcTemplate.queryForObject(FIND_MAX_UPDATED_AT, LocalDateTime.class);
        return max != null ? max.atZone(Payment.ZONA_HORARIA).toInstant() : java.time.Instant.EPOCH;
    }

    private RowMapper<Payment> paymentRowMapper() {
        return (rs, rowNum) -> {
            LocalDateTime fechaPago = rs.getTimestamp("fecha_pago").toLocalDateTime();
            LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
            LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
            java.sql.Date fechaApertura = rs.getDate("fecha_apertura");
            java.sql.Date fechaCierre = rs.getDate("fecha_cierre");
            int anioPeriodo = fechaApertura != null ? fechaApertura.toLocalDate().getYear() : 0;
            int mesPeriodo = fechaApertura != null ? fechaApertura.toLocalDate().getMonthValue() : 0;

            return new Payment(
                rs.getLong("id"),
                rs.getBigDecimal("monto_pago"),
                fechaPago,
                rs.getString("metodo_pago"),
                rs.getString("estado_pago"),
                rs.getLong("lecturas_id"),
                rs.getString("numero_recibo"),
                rs.getLong("cliente_id"),
                rs.getString("dpi_cliente"),
                rs.getString("nombre_completo").trim(),
                rs.getString("codigo_contador"),
                anioPeriodo,
                mesPeriodo,
                rs.getLong("usuario_id"),
                createdAt,
                updatedAt
            );
        };
    }
}