package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;

import java.util.Set;

/**
 * Tabla de decisión común a los 3 destinos (sección 6.4 del plan). Decide por código HTTP y
 * por código de error del contrato; nunca por el mensaje ni solo por {@code retryable}.
 */
public final class ResponseClassifier {

    private static final Set<String> DUPLICADO = Set.of("DUPLICATE_PAYMENT", "DOCUMENTO_DUPLICADO");

    private ResponseClassifier() {
    }

    public static Outcome classify(int httpStatus, String errorCode) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return Outcome.DELIVERED;
        }
        if (httpStatus == 409) {
            // Duplicado = el destino ya lo tiene. Cualquier otro 409 (llave reutilizada con
            // datos distintos) es un error nuestro: no se reintenta.
            return errorCode != null && DUPLICADO.contains(errorCode) ? Outcome.DELIVERED : Outcome.DEAD_LETTER;
        }
        if (httpStatus == 429 || httpStatus == 500 || httpStatus == 502 || httpStatus == 503) {
            return Outcome.RETRY;
        }
        if (httpStatus == 504 || httpStatus == 408) {
            // El gateway cortó: el pago pudo registrarse o no. Se consulta antes de reenviar.
            return Outcome.UNKNOWN;
        }
        if (httpStatus >= 500) {
            return Outcome.RETRY;
        }
        // 400, 401, 403 (llave/equipo), 404, 422 (regla de negocio) y demás 4xx.
        return Outcome.DEAD_LETTER;
    }
}
