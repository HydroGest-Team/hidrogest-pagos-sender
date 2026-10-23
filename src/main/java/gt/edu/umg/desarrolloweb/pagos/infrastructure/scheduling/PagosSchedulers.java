package gt.edu.umg.desarrolloweb.pagos.infrastructure.scheduling;

import gt.edu.umg.desarrolloweb.pagos.application.PaymentDispatcher;
import gt.edu.umg.desarrolloweb.pagos.application.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disparadores periódicos. Solo orquestan: la lógica vive en los casos de uso. */
@Component
@ConditionalOnProperty(prefix = "pagos.schedulers", name = "habilitados", havingValue = "true", matchIfMissing = true)
public class PagosSchedulers {

    private static final Logger log = LoggerFactory.getLogger(PagosSchedulers.class);

    private final PaymentDispatcher dispatcher;
    private final ReconciliationService reconciliation;

    public PagosSchedulers(PaymentDispatcher dispatcher, ReconciliationService reconciliation) {
        this.dispatcher = dispatcher;
        this.reconciliation = reconciliation;
    }

    @Scheduled(initialDelayString = "5s", fixedDelayString = "${pagos.producer.intervalo}")
    public void producer() {
        run("producer", dispatcher::dispatch);
    }

    @Scheduled(initialDelayString = "${pagos.reconciliador.intervalo}", fixedDelayString = "${pagos.reconciliador.intervalo}")
    public void reencolarVencidos() {
        run("reconciliador", reconciliation::requeueOverdue);
    }

    @Scheduled(initialDelayString = "30s", fixedDelayString = "${pagos.reconciliador.barrido}")
    public void barridoConsistencia() {
        run("barrido", reconciliation::sweep);
    }

    private void run(String tarea, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // Se reintenta en la siguiente ejecución; no se detiene el scheduler.
            log.error("Falló la tarea {}: {}", tarea, e.toString());
        }
    }
}
