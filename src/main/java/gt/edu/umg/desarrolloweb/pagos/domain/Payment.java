package gt.edu.umg.desarrolloweb.pagos.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record Payment(
    Long id,
    BigDecimal monto,
    LocalDateTime fechaPago,
    String metodoPago,
    String estadoPago,
    Long lecturaId,
    String numeroRecibo,
    Long clienteId,
    String dpiCliente,
    String nombreCompletoCliente,
    String codigoContador,
    Integer anioPeriodo,
    Integer mesPeriodo,
    Long usuarioId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static final ZoneId ZONA_HORARIA = ZoneId.of("America/Guatemala");

    public boolean esElegible() {
        return "PAGADO".equalsIgnoreCase(estadoPago);
    }

    public long montoEnCentavos() {
        return monto.setScale(2, java.math.RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
    }

    public String canalBanco() {
        return switch (metodoPago.toUpperCase()) {
            case "EFECTIVO" -> "CASH";
            case "CREDITO", "DEBITO" -> "CARD";
            default -> throw new IllegalArgumentException("Método no soportado: " + metodoPago);
        };
    }

    public String formaPagoErp() {
        return switch (metodoPago.toUpperCase()) {
            case "EFECTIVO" -> "EFECTIVO";
            case "CREDITO", "DEBITO" -> "TARJETA";
            default -> throw new IllegalArgumentException("Método no soportado: " + metodoPago);
        };
    }

    public String tipoDocumentoErp() {
        return switch (metodoPago.toUpperCase()) {
            case "EFECTIVO" -> "RECIBO_CAJA";
            case "CREDITO", "DEBITO" -> "VOUCHER_TARJETA";
            default -> throw new IllegalArgumentException("Método no soportado: " + metodoPago);
        };
    }

    public String metodoTercero() {
        return switch (metodoPago.toUpperCase()) {
            case "EFECTIVO" -> "CASH";
            case "CREDITO", "DEBITO" -> "CARD";
            default -> throw new IllegalArgumentException("Método no soportado: " + metodoPago);
        };
    }

    public String periodoFiscalErpAnio() {
        return String.valueOf(anioPeriodo);
    }

    public String periodoFiscalErpMes() {
        return String.valueOf(mesPeriodo);
    }

    public String periodoTercero() {
        return String.format("%04d-%02d", anioPeriodo, mesPeriodo);
    }

    public String descripcionBanco() {
        return "Servicio de agua " + periodoTercero();
    }

    public String descripcionDetalleErp() {
        return "Consumo " + mesPeriodo + " " + anioPeriodo;
    }

    public String clienteCode() {
        return "CLI-" + clienteId;
    }

    public LocalDateTime fechaPagoConZona() {
        return fechaPago.atZone(ZONA_HORARIA).toOffsetDateTime().toLocalDateTime();
    }
}