package gt.edu.umg.desarrolloweb.pagos.domain;

import java.util.Optional;

public interface DestinationGateway {
    SendResult send(Payment payment);
    Optional<Receipt> lookup(String idempotencyKey);
    boolean health();
    Destino destino();
    boolean supportsLookup();

    record SendResult(Outcome outcome, int httpStatus, String errorCode, String receiptId, String correlationId) {}
    record Receipt(String receiptId, String idempotencyKey, String teamCode, String status) {}
}