package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;

import java.time.Duration;

/** Qué debe hacer el listener con el mensaje después de procesarlo. */
public sealed interface DeliveryDecision {

    /** Nada más que hacer: ack. */
    record Ack() implements DeliveryDecision {}

    /** Publicar {@code next} en la cola de espera de {@code delay} y luego ack. */
    record Retry(DeliveryMessage next, Duration delay) implements DeliveryDecision {}

    /** Publicar en la DLQ del destino con la causa y luego ack. */
    record DeadLetter(DeliveryMessage message, String reason) implements DeliveryDecision {}

    DeliveryDecision ACK = new Ack();
}
