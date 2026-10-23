package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Duration;

/** Publicación de mensajes de entrega. Cada método vuelve solo cuando el broker confirmó. */
public interface DeliveryPublisher {

    /** Cola principal del destino: se procesa de inmediato. */
    void publish(DeliveryMessage message);

    /** Cola de espera cuyo TTL es {@code delay}; al vencer vuelve a la cola principal. */
    void publishRetry(DeliveryMessage message, Duration delay);

    /** Cola de dead-letter del destino, con la causa en un header. */
    void publishDeadLetter(DeliveryMessage message, String reason);
}
