package gt.edu.umg.desarrolloweb.pagos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Instantánea inmutable de un pago de HidroGest con los datos necesarios para los 3 destinos.
 * No incluye el DPI del cliente: nunca se envía a sistemas externos.
 *
 * @param fechaPago   {@code tb_pagos.fecha_pago} tal como la captura el cajero (sin zona)
 * @param periodo     período facturado de la lectura ({@code tb_periodos.fecha_apertura})
 * @param marcaTiempo {@code COALESCE(updated_at, created_at)} en el reloj de la BD; solo para el cursor
 */
public record Payment(
        long id,
        BigDecimal monto,
        LocalDateTime fechaPago,
        MetodoPago metodo,
        String estadoPago,
        String numeroRecibo,
        long clienteId,
        String nombreCliente,
        String codigoContador,
        YearMonth periodo,
        LocalDateTime marcaTiempo
) {
    public static final String ESTADO_PAGADO = "PAGADO";

    public Payment {
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(fechaPago, "fechaPago");
        Objects.requireNonNull(metodo, "metodo");
        Objects.requireNonNull(periodo, "periodo");
    }

    public boolean esElegible() {
        return ESTADO_PAGADO.equals(estadoPago);
    }

    /** Q150.75 → 15075. Falla si el monto tiene más de 2 decimales (nunca se redondea dinero). */
    public long montoEnCentavos() {
        return monto.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** Monto como texto con exactamente 2 decimales: {@code "150.75"}. */
    public String montoTexto() {
        return monto.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    public OffsetDateTime fechaPagoEn(ZoneId zona) {
        return fechaPago.atZone(zona).toOffsetDateTime();
    }

    /** Código estable del cliente en el monolito, por ejemplo {@code CLI-0001}. */
    public String codigoCliente() {
        return String.format("CLI-%04d", clienteId);
    }
}
