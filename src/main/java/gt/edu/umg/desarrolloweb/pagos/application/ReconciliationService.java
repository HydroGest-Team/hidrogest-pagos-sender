package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliador: reencola <b>solo</b> los destinos que faltan.
 * <ul>
 *   <li>{@link #requeueOverdue()}: estados abiertos cuyo próximo intento ya venció (mensaje perdido,
 *       caída entre crear el estado y publicar, o lease vencido de un consumer que murió).</li>
 *   <li>{@link #sweep()}: compara HidroGest contra DynamoDB y crea lo que falte para cada destino
 *       activo; así también se pone al día un destino recién activado.</li>
 * </ul>
 * {@code DEAD_LETTER} no se reintenta solo: requiere acción manual ({@link DeliveryAdminService}).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final LocalDateTime INICIO = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final DeliveryStateStore store;
    private final DeliveryPublisher publisher;
    private final PaymentSource payments;
    private final DeliveryEnqueuer enqueuer;
    private final DestinationRegistry destinations;
    private final DeliveryMetrics metrics;
    private final PagosProperties properties;
    private final Clock clock;

    public ReconciliationService(DeliveryStateStore store, DeliveryPublisher publisher, PaymentSource payments,
                                 DeliveryEnqueuer enqueuer, DestinationRegistry destinations,
                                 DeliveryMetrics metrics, PagosProperties properties, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.payments = payments;
        this.enqueuer = enqueuer;
        this.destinations = destinations;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public int requeueOverdue() {
        Instant limite = clock.instant().minus(properties.reconciliador().gracia());
        List<DeliveryState> vencidos = store.findOverdue(limite, properties.reconciliador().lote());
        int reencolados = 0;
        for (DeliveryState estado : vencidos) {
            if (!destinations.activos().contains(estado.destino())) {
                continue;
            }
            try {
                publisher.publish(new DeliveryMessage(estado.paymentId(), estado.destino(), estado.intentos(),
                        UUID.randomUUID().toString()));
                reencolados++;
            } catch (RuntimeException e) {
                log.error("No se pudo reencolar pago {} → {}: {}", estado.paymentId(), estado.destino(), e.toString());
            }
        }
        if (reencolados > 0) {
            log.info("Reconciliador: {} entregas vencidas reencoladas", reencolados);
        }
        refreshMetrics();
        return reencolados;
    }

    public int sweep() {
        int lote = properties.reconciliador().lote();
        PaymentSource.PageKey despues = null;
        int creados = 0;
        List<Payment> pagina;
        do {
            pagina = payments.findPagadosDesde(INICIO, despues, lote);
            if (pagina.isEmpty()) {
                break;
            }
            creados += enqueuer.enqueueMissing(pagina);
            Payment ultimo = pagina.get(pagina.size() - 1);
            despues = new PaymentSource.PageKey(ultimo.marcaTiempo(), ultimo.id());
        } while (pagina.size() == lote);
        if (creados > 0) {
            log.info("Barrido de consistencia: {} entregas faltantes creadas y encoladas", creados);
        }
        return creados;
    }

    public void refreshMetrics() {
        metrics.abiertos(store.countOpen());
    }
}
