package gt.edu.umg.desarrolloweb.pagos.infrastructure.rabbit;

import gt.edu.umg.desarrolloweb.pagos.application.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Topología declarativa (se crea al arrancar):
 * <pre>
 * pagos.exchange ──pagos.&lt;d&gt;──────────► pagos.&lt;d&gt;              (quorum; consumer)
 *               ──pagos.&lt;d&gt;.retry.5s──► pagos.&lt;d&gt;.retry.5s     (TTL 5 s; al vencer → pagos.&lt;d&gt;)
 *               ...                        ...                      (un tier por espera configurada)
 * pagos.dlx     ──pagos.&lt;d&gt;.dlq────────► pagos.&lt;d&gt;.dlq          (quorum; revisión manual)
 * </pre>
 * Reintentos no bloqueantes: el consumer hace ack del original y publica en la cola de espera.
 */
@Configuration
public class RabbitTopology {

    public static final String EXCHANGE = "pagos.exchange";
    public static final String DLX = "pagos.dlx";
    /** Si un mensaje falla así de veces sin llegar a ack (por ejemplo, cuerpo ilegible), va a la DLQ. */
    private static final int DELIVERY_LIMIT = 10;

    public static String mainQueue(Destino destino) {
        return "pagos." + destino.slug();
    }

    public static String dlq(Destino destino) {
        return mainQueue(destino) + ".dlq";
    }

    public static String retryQueue(Destino destino, Duration tier) {
        return mainQueue(destino) + ".retry." + label(tier);
    }

    /** {@code 5s}, {@code 30s}, {@code 2m}, {@code 10m}. */
    static String label(Duration tier) {
        long segundos = tier.toSeconds();
        return segundos >= 60 && segundos % 60 == 0 ? (segundos / 60) + "m" : segundos + "s";
    }

    @Bean
    public Declarables pagosTopology(PagosProperties properties) {
        DirectExchange exchange = ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
        List<Declarable> declarables = new ArrayList<>(List.of(exchange, dlx));

        for (Destino destino : Destino.values()) {
            Queue main = QueueBuilder.durable(mainQueue(destino))
                    .quorum()
                    .deliveryLimit(DELIVERY_LIMIT)
                    .deadLetterExchange(DLX)
                    .deadLetterRoutingKey(dlq(destino))
                    .build();
            Queue dead = QueueBuilder.durable(dlq(destino)).quorum().build();
            declarables.add(main);
            declarables.add(dead);
            declarables.add(bind(main, exchange));
            declarables.add(bind(dead, dlx));

            for (Duration tier : properties.reintentos().tiers()) {
                Queue retry = QueueBuilder.durable(retryQueue(destino, tier))
                        .ttl((int) tier.toMillis())
                        .deadLetterExchange(EXCHANGE)
                        .deadLetterRoutingKey(mainQueue(destino))
                        .build();
                declarables.add(retry);
                declarables.add(bind(retry, exchange));
            }
        }
        return new Declarables(declarables);
    }

    private static Binding bind(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(queue.getName());
    }
}
