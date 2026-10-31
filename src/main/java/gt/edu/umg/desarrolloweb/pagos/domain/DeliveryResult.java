package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Duration;

/**
 * Resultado de una llamada a un destino.
 *
 * @param httpStatus código HTTP recibido, o {@code null} si no hubo respuesta (timeout, red)
 * @param errorCode  {@code code}/{@code codigo_error} del destino, o un código local
 * @param receiptId  comprobante del destino ({@code receiptId}, {@code numero_comprobante}, {@code confirmationId})
 * @param retryAfter espera sugerida por el header {@code Retry-After}, o {@code null}
 * @param detail     texto corto para diagnóstico (nunca datos personales ni llaves)
 */
public record DeliveryResult(
        Outcome outcome,
        Integer httpStatus,
        String errorCode,
        String receiptId,
        Duration retryAfter,
        String detail
) {
    /** Código local: el circuit breaker del destino está abierto y no se hizo la llamada. */
    public static final String CIRCUITO_ABIERTO = "CIRCUITO_ABIERTO";
    public static final String VALIDACION_LOCAL = "VALIDACION_LOCAL";
    /** Código local: el destino ya tiene la llave registrada con datos de otro pago. */
    public static final String LLAVE_REUTILIZADA = "LLAVE_REUTILIZADA";

    public static DeliveryResult delivered(int httpStatus, String errorCode, String receiptId) {
        return new DeliveryResult(Outcome.DELIVERED, httpStatus, errorCode, receiptId, null, null);
    }

    public static DeliveryResult circuitOpen() {
        return new DeliveryResult(Outcome.RETRY, null, CIRCUITO_ABIERTO, null, null, "circuit breaker abierto");
    }

    public static DeliveryResult validacionLocal(String detail) {
        return new DeliveryResult(Outcome.DEAD_LETTER, null, VALIDACION_LOCAL, null, null, detail);
    }

    public boolean esCircuitoAbierto() {
        return CIRCUITO_ABIERTO.equals(errorCode);
    }
}
