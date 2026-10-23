package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Tabla de decisión de la sección 6.4 del plan, fila por fila, con los códigos de los 3 contratos. */
class ResponseClassifierTest {

    @ParameterizedTest(name = "HTTP {0} code={1} → {2}")
    @CsvSource(nullValues = "null", value = {
            "201, null,                   DELIVERED",
            "200, null,                   DELIVERED",
            // Duplicado = ya entregado (banco/tercero y ERP)
            "409, DUPLICATE_PAYMENT,      DELIVERED",
            "409, DOCUMENTO_DUPLICADO,    DELIVERED",
            // Misma llave con datos distintos: bug nuestro
            "409, IDEMPOTENCY_KEY_REUSED, DEAD_LETTER",
            "409, DOCUMENTO_REUTILIZADO,  DEAD_LETTER",
            "409, null,                   DEAD_LETTER",
            "400, VALIDATION_ERROR,       DEAD_LETTER",
            "400, VALIDACION,             DEAD_LETTER",
            // 403 de API Gateway ({\"message\":\"Forbidden\"}) llega sin código
            "403, null,                   DEAD_LETTER",
            "403, TEAM_MISMATCH,          DEAD_LETTER",
            "403, EQUIPO_NO_COINCIDE,     DEAD_LETTER",
            "422, AMOUNT_LIMIT_EXCEEDED,  DEAD_LETTER",
            "422, PAYMENT_DATE_IN_FUTURE, DEAD_LETTER",
            "422, UNSUPPORTED_CHANNEL,    DEAD_LETTER",
            "422, TOTAL_NO_CUADRA,        DEAD_LETTER",
            "422, PERIODO_CERRADO,        DEAD_LETTER",
            "422, PERIODO_FUTURO,         DEAD_LETTER",
            "422, DESCRIPCION_REQUERIDA,  DEAD_LETTER",
            "429, RATE_LIMITED,           RETRY",
            "429, null,                   RETRY",
            "500, INTERNAL_ERROR,         RETRY",
            "500, ERROR_INTERNO,          RETRY",
            "502, null,                   RETRY",
            "503, SERVICE_UNAVAILABLE,    RETRY",
            "503, SERVICIO_NO_DISPONIBLE, RETRY",
            // El gateway cortó: pudo registrarse o no
            "504, null,                   UNKNOWN",
    })
    void clasificaSegunHttpYCodigo(int status, String code, Outcome esperado) {
        assertThat(ResponseClassifier.classify(status, code)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"DUPLICATE_PAYMENT", "DOCUMENTO_DUPLICADO"})
    void unCodigoDeDuplicadoConOtroEstadoNoCuentaComoEntregado(String code) {
        assertThat(ResponseClassifier.classify(500, code)).isEqualTo(Outcome.RETRY);
    }
}
