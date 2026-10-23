package gt.edu.umg.desarrolloweb.pagos.domain;

import java.util.List;

/** Bitácora de intentos ("DynamoDB Logs" del diagrama): un registro por cada llamada a un destino. */
public interface AttemptLog {

    void record(AttemptRecord attempt);

    List<AttemptRecord> findByPaymentAndDestino(long paymentId, Destino destino);
}
