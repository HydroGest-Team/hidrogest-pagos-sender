package gt.edu.umg.desarrolloweb.pagos.domain;

public record DeliveryMessage(
    Long paymentId,
    String destino,
    int attempt,
    String correlationId,
    String idempotencyKey
) {}