package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.LookupResult;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;

/** Traduce el resultado de una consulta a un {@link DeliveryResult} para la bitácora. */
final class DeliveryProcessorSupport {

    private DeliveryProcessorSupport() {
    }

    static DeliveryResult asResult(LookupResult lookup) {
        if (lookup instanceof LookupResult.Found found) {
            return DeliveryResult.delivered(200, null, found.receiptId());
        }
        if (lookup instanceof LookupResult.Failed failed) {
            return failed.result();
        }
        return new DeliveryResult(Outcome.RETRY, 404, "NO_ENCONTRADO", null, null, null);
    }
}
