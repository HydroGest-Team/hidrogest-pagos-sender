package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeliveryPublisherImpl implements DeliveryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public DeliveryPublisherImpl(RabbitTemplate rabbitTemplate,
                                  @Value("${spring.rabbitmq.template.exchange:pagos.exchange}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    public void publish(DeliveryMessage message) {
        String correlationId = MDC.get("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = java.util.UUID.randomUUID().toString();
            MDC.put("X-Correlation-Id", correlationId);
        }
        final String finalCorrelationId = correlationId;

        CorrelationData correlationData = new CorrelationData(finalCorrelationId);
        correlationData.getFuture().whenComplete((result, ex) -> {
            if (ex != null) {
                // Log publisher confirm failure
            }
        });

        String routingKey = "pagos." + message.destino().toLowerCase();
        
        rabbitTemplate.convertAndSend(exchange, routingKey, message, m -> {
            m.getMessageProperties().setCorrelationId(finalCorrelationId);
            m.getMessageProperties().setHeader("x-attempt", message.attempt());
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return m;
        }, correlationData);
    }
}