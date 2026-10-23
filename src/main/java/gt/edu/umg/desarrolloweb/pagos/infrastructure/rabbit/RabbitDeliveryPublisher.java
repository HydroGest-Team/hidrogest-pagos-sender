package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import gt.edu.umg.desarrolloweb.pagos.application.Correlation;
import gt.edu.umg.desarrolloweb.pagos.application.RetryPolicy;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publica con <i>publisher confirms</i> y {@code mandatory}: cada publicación espera el ack del
 * broker y falla si el mensaje no se pudo enrutar. Así nada se pierde en silencio.
 */
@Component
public class RabbitDeliveryPublisher implements DeliveryPublisher {

    static final String HEADER_ATTEMPT = "x-attempt";
    static final String HEADER_DEAD_REASON = "x-pagos-dead-reason";
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate template;
    private final RetryPolicy retryPolicy;

    public RabbitDeliveryPublisher(RabbitTemplate template, RetryPolicy retryPolicy) {
        this.template = template;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public void publish(DeliveryMessage message) {
        send(RabbitTopology.EXCHANGE, RabbitTopology.mainQueue(message.destino()), message, null);
    }

    @Override
    public void publishRetry(DeliveryMessage message, Duration delay) {
        Duration tier = retryPolicy.atLeast(delay);
        send(RabbitTopology.EXCHANGE, RabbitTopology.retryQueue(message.destino(), tier), message, null);
    }

    @Override
    public void publishDeadLetter(DeliveryMessage message, String reason) {
        send(RabbitTopology.DLX, RabbitTopology.dlq(message.destino()), message, reason);
    }

    private void send(String exchange, String routingKey, DeliveryMessage message, String deadReason) {
        CorrelationData confirm = new CorrelationData(UUID.randomUUID().toString());
        template.convertAndSend(exchange, routingKey, message, m -> {
            var props = m.getMessageProperties();
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setCorrelationId(message.correlationId());
            props.setHeader(Correlation.HEADER, message.correlationId());
            props.setHeader(HEADER_ATTEMPT, message.attempt());
            if (deadReason != null) {
                props.setHeader(HEADER_DEAD_REASON, deadReason.length() > 250 ? deadReason.substring(0, 250) : deadReason);
            }
            return m;
        }, confirm);
        try {
            CorrelationData.Confirm result = confirm.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm.getReturned() != null) {
                throw new AmqpException("Mensaje no enrutable a " + routingKey + ": " + confirm.getReturned().getReplyText());
            }
            if (!result.isAck()) {
                throw new AmqpException("El broker rechazó el mensaje a " + routingKey + ": " + result.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrumpido esperando confirmación del broker", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("Sin confirmación del broker para " + routingKey, e);
        }
    }
}
