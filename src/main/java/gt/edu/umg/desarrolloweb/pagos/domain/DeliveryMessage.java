package gt.edu.umg.desarrolloweb.pagos.domain;

/**
 * Mensaje de la cola: solo identificadores, sin datos personales. El consumer vuelve a
 * leer el pago de HidroGest y el estado de DynamoDB.
 *
 * @param attempt número de entregas de este mensaje en la cola (informativo; el contador
 *                real de intentos vive en DynamoDB)
 */
public record DeliveryMessage(long paymentId, Destino destino, int attempt, String correlationId) {

    public DeliveryMessage nextAttempt() {
        return new DeliveryMessage(paymentId, destino, attempt + 1, correlationId);
    }
}
