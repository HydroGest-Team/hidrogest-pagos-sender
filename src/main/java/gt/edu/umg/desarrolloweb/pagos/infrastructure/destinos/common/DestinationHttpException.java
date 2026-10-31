package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Respuesta HTTP no exitosa de un destino, ya decodificada.
 *
 * @see ContractErrorDecoder
 */
public class DestinationHttpException extends RuntimeException {

    private final int status;
    private final String code;
    private final String originalReceiptId;
    private final BigDecimal originalAmount;
    private final transient Duration retryAfter;

    public DestinationHttpException(int status, String code, String originalReceiptId, BigDecimal originalAmount,
                                    Duration retryAfter) {
        super("HTTP " + status + (code == null ? "" : " " + code), null, false, false);
        this.status = status;
        this.code = code;
        this.originalReceiptId = originalReceiptId;
        this.originalAmount = originalAmount;
        this.retryAfter = retryAfter;
    }

    public int status() {
        return status;
    }

    /** {@code code} / {@code codigo_error} del contrato, o {@code null} si el cuerpo no lo trae (API Gateway). */
    public String code() {
        return code;
    }

    /** Comprobante original en un 409 duplicado. */
    public String originalReceiptId() {
        return originalReceiptId;
    }

    /** Monto con el que el destino registró la llave en un 409 duplicado, o {@code null} si no vino. */
    public BigDecimal originalAmount() {
        return originalAmount;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
