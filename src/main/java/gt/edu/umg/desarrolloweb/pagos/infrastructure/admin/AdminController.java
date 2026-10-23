package gt.edu.umg.desarrolloweb.pagos.infrastructure.admin;

import gt.edu.umg.desarrolloweb.pagos.application.DeliveryAdminService;
import gt.edu.umg.desarrolloweb.pagos.application.PaymentDispatcher;
import gt.edu.umg.desarrolloweb.pagos.application.ReconciliationService;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operación manual (Basic Auth, rol ADMIN). Ver el runbook en {@code docs/RUNBOOK.md}.
 * <ul>
 *   <li>{@code GET  /admin/pagos/{id}}: estado por destino, comprobantes y bitácora de intentos.</li>
 *   <li>{@code POST /admin/pagos/{id}/destinos/{destino}/reencolar}: saca un DEAD_LETTER y lo reenvía.</li>
 *   <li>{@code POST /admin/producer} y {@code POST /admin/reconciliar}: ejecutan el ciclo ya.</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DeliveryAdminService admin;
    private final PaymentDispatcher dispatcher;
    private final ReconciliationService reconciliation;

    public AdminController(DeliveryAdminService admin, PaymentDispatcher dispatcher,
                           ReconciliationService reconciliation) {
        this.admin = admin;
        this.dispatcher = dispatcher;
        this.reconciliation = reconciliation;
    }

    @GetMapping("/pagos/{paymentId}")
    public ResponseEntity<DeliveryAdminService.PaymentStatus> status(@PathVariable long paymentId) {
        DeliveryAdminService.PaymentStatus status = admin.status(paymentId);
        return status.destinos().isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(status);
    }

    @PostMapping("/pagos/{paymentId}/destinos/{destino}/reencolar")
    public ResponseEntity<Map<String, Object>> requeue(@PathVariable long paymentId, @PathVariable Destino destino) {
        if (!admin.requeueDeadLetter(paymentId, destino)) {
            return ResponseEntity.status(409).body(Map.of(
                    "paymentId", paymentId, "destino", destino,
                    "error", "la entrega no existe o no está en DEAD_LETTER"));
        }
        return ResponseEntity.accepted().body(Map.of("paymentId", paymentId, "destino", destino, "estado", "PENDIENTE"));
    }

    @PostMapping("/producer")
    public Map<String, Object> runProducer() {
        return Map.of("encolados", dispatcher.dispatch());
    }

    @PostMapping("/reconciliar")
    public Map<String, Object> reconcile() {
        int vencidos = reconciliation.requeueOverdue();
        int faltantes = reconciliation.sweep();
        return Map.of("reencoladosPorVencimiento", vencidos, "faltantesCreados", faltantes);
    }
}
