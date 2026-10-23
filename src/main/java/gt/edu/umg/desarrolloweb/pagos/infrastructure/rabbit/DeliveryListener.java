package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import com.rabbitmq.client.Channel;
import gt.edu.umg.desarrolloweb.pagos.application.Correlation;
import gt.edu.umg.desarrolloweb.pagos.application.DeliveryDecision;
import gt.edu.umg.desarrolloweb.pagos.application.DeliveryProcessor;
import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Un listener por destino, con su propia concurrencia (aislamiento tipo bulkhead: un destino
 * lento no frena a los otros). Solo orquesta: la lógica está en {@link DeliveryProcessor}.
 * Ack manual, siempre después de persistir el estado y publicar el siguiente paso.
 */
@Component
public class DeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryListener.class);

    private final DeliveryProcessor processor;
    private final DeliveryPublisher publisher;
    private final PagosProperties properties;

    public DeliveryListener(DeliveryProcessor processor, DeliveryPublisher publisher, PagosProperties properties) {
        this.processor = processor;
        this.publisher = publisher;
        this.properties = properties;
    }

    @RabbitListener(id = "listener-banco", queues = "pagos.banco",
            concurrency = "${pagos.consumer.concurrencia.banco:2}",
            autoStartup = "#{'${pagos.destinos-activos}'.contains('BANCO')}")
    public void banco(DeliveryMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(message, channel, tag);
    }

    @RabbitListener(id = "listener-erp", queues = "pagos.erp",
            concurrency = "${pagos.consumer.concurrencia.erp:2}",
            autoStartup = "#{'${pagos.destinos-activos}'.contains('ERP')}")
    public void erp(DeliveryMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(message, channel, tag);
    }

    @RabbitListener(id = "listener-tercero", queues = "pagos.tercero",
            concurrency = "${pagos.consumer.concurrencia.tercero:2}",
            autoStartup = "#{'${pagos.destinos-activos}'.contains('TERCERO')}")
    public void tercero(DeliveryMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(message, channel, tag);
    }

    private void handle(DeliveryMessage message, Channel channel, long tag) throws IOException {
        MDC.put(Correlation.MDC_KEY, message.correlationId());
        MDC.put("paymentId", Long.toString(message.paymentId()));
        MDC.put("destino", message.destino().name());
        try {
            DeliveryDecision decision = processor.process(message);
            if (decision instanceof DeliveryDecision.Retry retry) {
                publisher.publishRetry(retry.next(), retry.delay());
            } else if (decision instanceof DeliveryDecision.DeadLetter dead) {
                publisher.publishDeadLetter(dead.message(), dead.reason());
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            // Falla de infraestructura (DynamoDB, MariaDB o el broker). Se reprograma con una
            // espera mayor al lease para que el reclamo actual haya vencido cuando vuelva.
            log.error("Error procesando el mensaje; se reprograma: {}", e.toString());
            try {
                publisher.publishRetry(message.nextAttempt(), properties.reconciliador().lease());
                channel.basicAck(tag, false);
            } catch (Exception publishError) {
                log.error("No se pudo reprogramar; se devuelve a la cola: {}", publishError.toString());
                channel.basicNack(tag, false, true);
            }
        } finally {
            MDC.remove(Correlation.MDC_KEY);
            MDC.remove("paymentId");
            MDC.remove("destino");
        }
    }
}
