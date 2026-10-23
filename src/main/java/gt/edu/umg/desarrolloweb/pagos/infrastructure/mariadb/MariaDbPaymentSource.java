package gt.edu.umg.desarrolloweb.pagos.infrastructure.mariadb;

import gt.edu.umg.desarrolloweb.pagos.domain.MetodoPago;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Lectura de pagos en la BD de HidroGest. <b>Solo SELECT</b>: este servicio nunca escribe ni crea
 * tablas en esa base. Camino: {@code tb_pagos → tb_lecturas → tb_contadores → tb_clientes} y
 * {@code tb_lecturas → tb_periodos}. No se selecciona el DPI.
 */
@Repository
public class MariaDbPaymentSource implements PaymentSource {

    /** Laravel deja {@code updated_at} en NULL si la fila se insertó fuera de Eloquent. */
    private static final String MARCA = "COALESCE(p.updated_at, p.created_at, p.fecha_pago)";

    private static final String SELECT = """
            SELECT p.id, p.monto_pago, p.fecha_pago, p.metodo_pago, p.estado_pago,
                   l.numero_recibo, c.id AS cliente_id,
                   CONCAT_WS(' ', c.nombre1_cliente, c.nombre2_cliente, c.nombre3_cliente,
                             c.apellido1_cliente, c.apellido2_cliente, c.apellido3_cliente) AS nombre_cliente,
                   ct.codigo_contador, per.fecha_apertura,
                   %s AS marca_tiempo
            FROM tb_pagos p
            JOIN tb_lecturas l    ON l.id = p.lecturas_id
            JOIN tb_contadores ct ON ct.id = l.contador_id
            JOIN tb_clientes c    ON c.id = ct.cliente_id
            JOIN tb_periodos per  ON per.id = l.periodo_id
            """.formatted(MARCA);

    private static final String PAGADOS_DESDE = SELECT + """
            WHERE p.estado_pago = 'PAGADO'
              AND %1$s >= :desde
            ORDER BY %1$s, p.id
            LIMIT :limite
            """.formatted(MARCA);

    private static final String PAGADOS_DESPUES = SELECT + """
            WHERE p.estado_pago = 'PAGADO'
              AND %1$s >= :desde
              AND (%1$s > :marca OR (%1$s = :marca AND p.id > :id))
            ORDER BY %1$s, p.id
            LIMIT :limite
            """.formatted(MARCA);

    private static final String POR_ID = SELECT + "WHERE p.id = :id";

    private static final RowMapper<Payment> MAPPER = (rs, n) -> new Payment(
            rs.getLong("id"),
            rs.getBigDecimal("monto_pago"),
            rs.getObject("fecha_pago", LocalDateTime.class),
            MetodoPago.desdeBd(rs.getString("metodo_pago")),
            rs.getString("estado_pago"),
            rs.getString("numero_recibo"),
            rs.getLong("cliente_id"),
            rs.getString("nombre_cliente"),
            rs.getString("codigo_contador"),
            YearMonth.from(rs.getObject("fecha_apertura", LocalDateTime.class)),
            rs.getObject("marca_tiempo", LocalDateTime.class));

    private final NamedParameterJdbcTemplate jdbc;

    public MariaDbPaymentSource(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Payment> findPagadosDesde(LocalDateTime desde, PageKey despues, int limite) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("desde", desde)
                .addValue("limite", limite);
        if (despues == null) {
            return jdbc.query(PAGADOS_DESDE, params, MAPPER);
        }
        params.addValue("marca", despues.marcaTiempo()).addValue("id", despues.id());
        return jdbc.query(PAGADOS_DESPUES, params, MAPPER);
    }

    @Override
    public Optional<Payment> findById(long id) {
        return jdbc.query(POR_ID, new MapSqlParameterSource("id", id), MAPPER).stream().findFirst();
    }
}
