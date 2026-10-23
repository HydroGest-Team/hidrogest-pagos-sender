package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliveryStateStore {
    boolean createIfNotExists(DeliveryState state);
    Optional<DeliveryState> findByPaymentIdAndDestino(Long paymentId, String destino);
    boolean claimForProcessing(Long paymentId, String destino, Instant leaseUntil);
    boolean updateStateConditionally(DeliveryState expected, DeliveryState updated);
    boolean markDelivered(Long paymentId, String destino, String receiptId);
    boolean markDeadLetter(Long paymentId, String destino, int httpStatus, String errorCode);
    boolean updateForRetry(Long paymentId, String destino, Instant nextAttemptAt, int httpStatus, String errorCode);
    boolean updateForUnknown(Long paymentId, String destino);
    List<DeliveryState> findAbiertosConNextAttemptAntesDe(Instant now);
    List<DeliveryState> findEnVueloConLeaseVencido(Instant now);
    List<DeliveryState> findPendientesSinActividadDesde(Instant since);
    List<Long> findPagosSinEstadoParaDestino(String destino, Instant desde);
}