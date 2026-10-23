package gt.edu.umg.desarrolloweb.pagos.domain;

/** Resultado de un intento de entrega, según la tabla de decisión (sección 6.4 del plan). */
public enum Outcome {
    DELIVERED,
    RETRY,
    UNKNOWN,
    DEAD_LETTER
}
