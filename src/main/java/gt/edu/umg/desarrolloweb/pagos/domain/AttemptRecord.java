package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;

/** Registro de bitácora de un intento (tabla {@code pagos_intentos}). */
public record AttemptRecord(
        long paymentId,
        Destino destino,
        int intento,
        Instant timestamp,
        String operacion,
        Outcome outcome,
        Integer httpStatus,
        String errorCode,
        String receiptId,
        String correlationId,
        long duracionMs
) {}
