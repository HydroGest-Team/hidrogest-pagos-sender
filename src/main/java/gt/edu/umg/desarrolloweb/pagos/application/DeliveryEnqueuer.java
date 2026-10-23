package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crea el estado {@code PENDIENTE} de cada (pago, destino activo) que aún no lo tenga y publica
 * su mensaje. Releer pagos es inofensivo: el estado se crea con escritura condicional y solo
 * quien lo crea publica. Si la publicación falla, el reconciliador lo reencola después.
 */
@Component
public class DeliveryEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEnqueuer.class);

    private final DeliveryStateStore store;
    private final DeliveryPublisher publisher;
    private final DestinationRegistry destinations;
    private final IdempotencyKeys keys;
    private final PagosProperties properties;
    private final Clock clock;

    public DeliveryEnqueuer(DeliveryStateStore store, DeliveryPublisher publisher, DestinationRegistry destinations,
                            IdempotencyKeys keys, PagosProperties properties, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.destinations = destinations;
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return cantidad de entregas nuevas encoladas */
    public int enqueueMissing(List<Payment> pagos) {
        if (pagos.isEmpty()) {
            return 0;
        }
        List<Destino> activos = destinations.activos();
        Set<DeliveryStateStore.Key> existentes = store.existing(pagos.stream().map(Payment::id).toList(), activos);
        int nuevos = 0;
        for (Payment pago : pagos) {
            for (Destino destino : activos) {
                if (existentes.contains(new DeliveryStateStore.Key(pago.id(), destino)) || antesDelCorte(pago, destino)) {
                    continue;
                }
                if (store.createPending(pago.id(), destino, keys.forPayment(pago.id()), clock.instant())) {
                    publicar(pago.id(), destino);
                    nuevos++;
                }
            }
        }
        return nuevos;
    }

    private boolean antesDelCorte(Payment pago, Destino destino) {
        LocalDateTime corte = properties.desde().get(destino);
        return corte != null && pago.marcaTiempo() != null && pago.marcaTiempo().isBefore(corte);
    }

    private void publicar(long paymentId, Destino destino) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(Correlation.MDC_KEY, correlationId);
        try {
            publisher.publish(new DeliveryMessage(paymentId, destino, 0, correlationId));
            log.info("Encolado pago {} → {}", paymentId, destino);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar pago {} → {}; el reconciliador lo reencolará: {}", paymentId, destino, e.toString());
        } finally {
            MDC.remove(Correlation.MDC_KEY);
        }
    }
}
