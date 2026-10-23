package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;

/**
 * Estado de entrega de un pago a un destino. {@code version} crece en cada cambio y sirve
 * para las escrituras condicionales: quien reclamó el envío es el único que puede cerrarlo.
 */
public record DeliveryState(
        long paymentId,
        Destino destino,
        EstadoEntrega estado,
        int intentos,
        Instant nextAttemptAt,
        Instant leaseUntil,
        String idempotencyKey,
        String receiptId,
        Integer lastHttpStatus,
        String lastErrorCode,
        String lastErrorDetail,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public boolean esTerminal() {
        return estado.esTerminal();
    }
}
