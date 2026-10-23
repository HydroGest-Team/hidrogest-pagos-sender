package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    /** JSON con el ObjectMapper de Spring Boot; Boot lo usa en el RabbitTemplate y en los listeners. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** Mensajes en la DLQ de cada destino ({@code pagos.dlq.mensajes}). */
    @Bean
    public InitializingBean dlqGauges(MeterRegistry registry, AmqpAdmin admin) {
        return () -> {
            for (Destino destino : Destino.values()) {
                Gauge.builder("pagos.dlq.mensajes", () -> depth(admin, RabbitTopology.dlq(destino)))
                        .description("Mensajes en dead-letter pendientes de revisión")
                        .tag("destino", destino.name())
                        .register(registry);
            }
        };
    }

    private static double depth(AmqpAdmin admin, String queue) {
        try {
            QueueInformation info = admin.getQueueInfo(queue);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
