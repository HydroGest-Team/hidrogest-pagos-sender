package gt.edu.umg.desarrolloweb.pagos.infrastructure.admin;

import gt.edu.umg.desarrolloweb.pagos.application.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ReconciliationService reconciliationService;

    public AdminController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/requeue-dlq")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> requeueDeadLetter(
            @RequestParam Long paymentId,
            @RequestParam String destino) {
        reconciliationService.reenqueueDeadLetter(paymentId, destino);
        return ResponseEntity.ok(Map.of("status", "requeued", "paymentId", paymentId, "destino", destino));
    }

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> forceReconcile() {
        reconciliationService.reconcile();
        return ResponseEntity.ok(Map.of("status", "reconciliation triggered"));
    }

    @GetMapping("/payment/{paymentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> paymentStatus(@PathVariable Long paymentId) {
        boolean complete = reconciliationService.isPagoCompleto(paymentId);
        long pending = reconciliationService.countPendientes();
        return ResponseEntity.ok(Map.of(
            "paymentId", paymentId,
            "complete", complete,
            "globalPending", pending
        ));
    }
}