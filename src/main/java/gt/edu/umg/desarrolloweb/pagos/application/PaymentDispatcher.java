package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.CursorStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Producer: lee los pagos {@code PAGADO} cuya marca de tiempo avanzó desde la última corrida y
 * encola los que falten. No usa un cursor por {@code id}: un pago viejo puede pasar a
 * {@code PAGADO} después. Se relee un traslape para tolerar transacciones que confirman tarde.
 */
@Service
public class PaymentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentDispatcher.class);
    static final String CURSOR = "producer";
    private static final LocalDateTime INICIO = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PaymentSource payments;
    private final CursorStore cursors;
    private final DeliveryEnqueuer enqueuer;
    private final PagosProperties properties;

    public PaymentDispatcher(PaymentSource payments, CursorStore cursors, DeliveryEnqueuer enqueuer,
                             PagosProperties properties) {
        this.payments = payments;
        this.cursors = cursors;
        this.enqueuer = enqueuer;
        this.properties = properties;
    }

    public int dispatch() {
        LocalDateTime cursor = cursors.get(CURSOR).orElse(null);
        LocalDateTime desde = cursor == null ? INICIO : cursor.minus(properties.producer().traslape());
        int lote = properties.producer().lote();

        PaymentSource.PageKey despues = null;
        LocalDateTime maximo = cursor;
        int encolados = 0;
        List<Payment> pagina;
        do {
            pagina = payments.findPagadosDesde(desde, despues, lote);
            if (pagina.isEmpty()) {
                break;
            }
            encolados += enqueuer.enqueueMissing(pagina);
            Payment ultimo = pagina.get(pagina.size() - 1);
            despues = new PaymentSource.PageKey(ultimo.marcaTiempo(), ultimo.id());
            if (maximo == null || ultimo.marcaTiempo().isAfter(maximo)) {
                maximo = ultimo.marcaTiempo();
            }
        } while (pagina.size() == lote);

        if (maximo != null && !maximo.equals(cursor)) {
            cursors.put(CURSOR, maximo);
        }
        if (encolados > 0) {
            log.info("Producer: {} entregas nuevas encoladas (cursor {})", encolados, maximo);
        }
        return encolados;
    }
}
