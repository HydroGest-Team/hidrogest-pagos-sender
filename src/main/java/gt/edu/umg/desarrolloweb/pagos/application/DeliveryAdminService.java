package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLog;
import gt.edu.umg.desarrolloweb.pagos.domain.AttemptRecord;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Operaciones manuales: consultar el estado de un pago y reencolar un dead-letter ya corregido. */
@Service
public class DeliveryAdminService {

    private final DeliveryStateStore store;
    private final AttemptLog attemptLog;
    private final DeliveryPublisher publisher;
    private final DestinationRegistry destinations;
    private final Clock clock;

    public DeliveryAdminService(DeliveryStateStore store, AttemptLog attemptLog, DeliveryPublisher publisher,
                                DestinationRegistry destinations, Clock clock) {
        this.store = store;
        this.attemptLog = attemptLog;
        this.publisher = publisher;
        this.destinations = destinations;
        this.clock = clock;
    }

    public PaymentStatus status(long paymentId) {
        List<DeliveryState> estados = store.findByPayment(paymentId);
        List<DestinoStatus> porDestino = estados.stream()
                .map(e -> new DestinoStatus(e, attemptLog.findByPaymentAndDestino(paymentId, e.destino())))
                .toList();
        boolean completo = !estados.isEmpty() && destinations.activos().stream().allMatch(d -> estados.stream()
                .anyMatch(s -> s.destino() == d && s.estado() == EstadoEntrega.ENTREGADO));
        return new PaymentStatus(paymentId, completo, porDestino);
    }

    /** @return {@code false} si la entrega no existe o no está en {@code DEAD_LETTER} */
    public boolean requeueDeadLetter(long paymentId, Destino destino) {
        if (!store.resetDeadLetter(paymentId, destino, clock.instant())) {
            return false;
        }
        publisher.publish(new DeliveryMessage(paymentId, destino, 0, UUID.randomUUID().toString()));
        return true;
    }

    public record PaymentStatus(long paymentId, boolean completo, List<DestinoStatus> destinos) {}

    public record DestinoStatus(DeliveryState estado, List<AttemptRecord> intentos) {}
}
