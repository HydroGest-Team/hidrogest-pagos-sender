package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import java.util.List;

public interface PaymentSource {
    List<Payment> findElegiblesSince(Instant since, int limit);
    Payment findById(Long id);
    Instant findMaxUpdatedAt();
}